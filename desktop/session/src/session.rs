//! Adapter de sessão do canal desktop (ADR-0029).
//!
//! Responsabilidades: autenticar, guardar o refresh no cofre, manter o access
//! JWT **só na memória deste processo**, renovar uma única vez em `401` e
//! encerrar a sessão. Nenhuma regra financeira: a API decide renda, saldo,
//! carry e autorização.

use crate::cookies::parse_refresh_cookie;
use crate::http::{HttpError, HttpRequest, HttpResponse, HttpTransport, Method};
use crate::logger::Logger;
use crate::vault::{SecretVault, StoredRefresh, VaultError};
use std::sync::{Arc, Mutex};

pub const LOGIN_PATH: &str = "/api/v1/auth/login";
pub const MFA_VERIFY_PATH: &str = "/api/v1/auth/mfa/verify";
pub const REFRESH_PATH: &str = "/api/v1/auth/refresh";
pub const LOGOUT_PATH: &str = "/api/v1/auth/logout";
pub const ME_PATH: &str = "/api/v1/users/me";

/// Resultado de uma tentativa de autenticação. Nunca carrega token.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AuthOutcome {
    Authenticated { email: String },
    MfaRequired { challenge_id: String, expires_in: u64 },
}

/// Resposta crua de uma rota de recurso, repassada à interface.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ApiReply {
    pub status: u16,
    pub body: String,
}

#[derive(Debug, thiserror::Error)]
pub enum SessionError {
    /// Mensagem genérica de propósito: não revela se a conta existe.
    #[error("E-mail ou senha incorretos.")]
    InvalidCredentials,
    #[error("Código de verificação inválido ou expirado.")]
    MfaRejected,
    #[error("Sessão expirada. Entre de novo.")]
    AuthRequired,
    #[error("{0}")]
    VaultUnavailable(String),
    #[error("A API recusou a operação (HTTP {status}).")]
    Api { status: u16 },
    #[error("Não foi possível falar com a API. Verifique a conexão.")]
    Network,
    #[error("A API respondeu em formato inesperado.")]
    Malformed,
}

impl From<HttpError> for SessionError {
    fn from(_: HttpError) -> Self {
        SessionError::Network
    }
}

impl From<VaultError> for SessionError {
    fn from(error: VaultError) -> Self {
        match error {
            VaultError::Unavailable(message) => SessionError::VaultUnavailable(message),
            VaultError::Refused | VaultError::Corrupted => {
                SessionError::VaultUnavailable(crate::vault::unavailable_message())
            }
        }
    }
}

pub struct SessionAdapter<H: HttpTransport, V: SecretVault> {
    http: H,
    vault: V,
    logger: Arc<dyn Logger>,
    /// Access JWT. Só memória: nunca vai ao cofre, a arquivo ou ao webview.
    access: Mutex<Option<String>>,
}

impl<H: HttpTransport, V: SecretVault> SessionAdapter<H, V> {
    pub fn new(http: H, vault: V, logger: Arc<dyn Logger>) -> Self {
        Self {
            http,
            vault,
            logger,
            access: Mutex::new(None),
        }
    }

    /// Acesso ao transporte injetado. Existe para o teste inspecionar o que
    /// saiu na rede; o aplicativo não usa.
    pub fn transport_for_test(&self) -> &H {
        &self.http
    }

    /// Acesso ao cofre injetado, pelo mesmo motivo de `transport_for_test`.
    pub fn vault_for_test(&self) -> &V {
        &self.vault
    }

    pub fn is_authenticated(&self) -> bool {
        self.access.lock().expect("access mutex").is_some()
    }

    fn access_token(&self) -> Option<String> {
        self.access.lock().expect("access mutex").clone()
    }

    fn set_access(&self, token: Option<String>) {
        *self.access.lock().expect("access mutex") = token;
    }

    /// Apaga tudo o que identifica a sessão, em memória e no cofre.
    fn drop_session(&self) {
        self.set_access(None);
        let _ = self.vault.clear();
    }

    pub async fn login(&self, email: &str, password: &str) -> Result<AuthOutcome, SessionError> {
        let payload = serde_json::json!({ "email": email, "password": password }).to_string();
        let response = self
            .http
            .send(
                HttpRequest::new(Method::Post, LOGIN_PATH)
                    .header("Accept", "application/json")
                    .json_body(payload),
            )
            .await?;

        if !response.is_success() {
            // Sem e-mail, sem senha e sem corpo no log.
            self.logger
                .log(&format!("login recusado (HTTP {})", response.status));
            return Err(match response.status {
                400 | 401 | 403 | 404 => SessionError::InvalidCredentials,
                status => SessionError::Api { status },
            });
        }

        let body = parse_json(&response.body)?;
        if body.get("mfaRequired").and_then(|v| v.as_bool()) == Some(true) {
            let challenge_id = string_field(&body, "challengeId")?;
            let expires_in = body
                .get("expiresIn")
                .and_then(|v| v.as_u64())
                .unwrap_or(300);
            self.logger.log("login exige segundo fator");
            return Ok(AuthOutcome::MfaRequired {
                challenge_id,
                expires_in,
            });
        }

        self.persist_auth(&response, &body)?;
        let email = self.fetch_email().await?;
        self.logger.log("sessão autenticada");
        Ok(AuthOutcome::Authenticated { email })
    }

    pub async fn verify_mfa(
        &self,
        challenge_id: &str,
        code: &str,
    ) -> Result<AuthOutcome, SessionError> {
        let payload =
            serde_json::json!({ "challengeId": challenge_id, "code": code }).to_string();
        let response = self
            .http
            .send(
                HttpRequest::new(Method::Post, MFA_VERIFY_PATH)
                    .header("Accept", "application/json")
                    .json_body(payload),
            )
            .await?;

        if !response.is_success() {
            // O código TOTP não entra no log.
            self.logger
                .log(&format!("segundo fator recusado (HTTP {})", response.status));
            return Err(match response.status {
                400 | 401 | 403 => SessionError::MfaRejected,
                status => SessionError::Api { status },
            });
        }

        let body = parse_json(&response.body)?;
        self.persist_auth(&response, &body)?;
        let email = self.fetch_email().await?;
        self.logger.log("sessão autenticada após segundo fator");
        Ok(AuthOutcome::Authenticated { email })
    }

    /// Arranque: `Ok(Some(email))` restaurou; `Ok(None)` vai à tela de login.
    pub async fn restore(&self) -> Result<Option<String>, SessionError> {
        let stored = match self.vault.load() {
            Ok(Some(stored)) => stored,
            Ok(None) => {
                self.logger.log("arranque sem sessão guardada");
                return Ok(None);
            }
            Err(VaultError::Unavailable(message)) => {
                return Err(SessionError::VaultUnavailable(message))
            }
            Err(_) => {
                // Entrada ilegível: trata como ausência, sem derrubar o arranque.
                let _ = self.vault.clear();
                self.logger.log("entrada do cofre ilegível; sessão descartada");
                return Ok(None);
            }
        };

        match self.renew(&stored).await {
            Ok(()) => {
                let email = self.fetch_email().await?;
                self.logger.log("sessão restaurada no arranque");
                Ok(Some(email))
            }
            Err(SessionError::AuthRequired) => {
                self.logger.log("refresh guardado não serve mais");
                Ok(None)
            }
            Err(error) => Err(error),
        }
    }

    pub async fn logout(&self) -> Result<(), SessionError> {
        let token = self.access_token();
        let result = match token {
            Some(token) => {
                self.http
                    .send(
                        HttpRequest::new(Method::Post, LOGOUT_PATH)
                            .header("Accept", "application/json")
                            .header("Authorization", format!("Bearer {token}")),
                    )
                    .await
            }
            None => Ok(HttpResponse::new(204, "")),
        };
        // A sessão local vai embora mesmo se a revogação falhar.
        self.drop_session();
        self.logger.log("sessão encerrada");
        result.map(|_| ()).map_err(SessionError::from)
    }

    /// Chamada autenticada a uma rota de recurso, com uma única renovação em `401`.
    pub async fn api(
        &self,
        method: Method,
        path: &str,
        body: Option<String>,
    ) -> Result<ApiReply, SessionError> {
        let response = self.send_authenticated(method, path, body.clone()).await?;
        if response.status != 401 {
            return Ok(ApiReply {
                status: response.status,
                body: response.body,
            });
        }

        let stored = match self.vault.load() {
            Ok(Some(stored)) => stored,
            _ => {
                self.drop_session();
                return Err(SessionError::AuthRequired);
            }
        };
        self.renew(&stored).await?;

        let retried = self.send_authenticated(method, path, body).await?;
        if retried.status == 401 {
            // Uma renovação, uma repetição: não insiste.
            self.drop_session();
            return Err(SessionError::AuthRequired);
        }
        Ok(ApiReply {
            status: retried.status,
            body: retried.body,
        })
    }

    async fn send_authenticated(
        &self,
        method: Method,
        path: &str,
        body: Option<String>,
    ) -> Result<HttpResponse, SessionError> {
        let token = self.access_token().ok_or(SessionError::AuthRequired)?;
        let mut request = HttpRequest::new(method, path)
            .header("Accept", "application/json")
            .header("Authorization", format!("Bearer {token}"));
        if let Some(body) = body {
            request = request.json_body(body);
        }
        Ok(self.http.send(request).await?)
    }

    /// Troca o refresh guardado por um access novo. Falha limpa a sessão.
    ///
    /// A API rotaciona o refresh: a resposta traz um `Set-Cookie` novo, que
    /// substitui a entrada do cofre.
    async fn renew(&self, stored: &StoredRefresh) -> Result<(), SessionError> {
        let response = self
            .http
            .send(
                HttpRequest::new(Method::Post, REFRESH_PATH)
                    .header("Accept", "application/json")
                    // O refresh viaja como cookie, igual ao mobile e ao MCP.
                    .header(
                        "Cookie",
                        format!("{}={}", stored.cookie_name, stored.value),
                    ),
            )
            .await?;

        if !response.is_success() {
            self.logger
                .log(&format!("renovação recusada (HTTP {})", response.status));
            self.drop_session();
            return Err(SessionError::AuthRequired);
        }

        let body = parse_json(&response.body)?;
        match self.persist_auth(&response, &body) {
            Ok(()) => {
                self.logger.log("sessão renovada");
                Ok(())
            }
            // Cofre indisponível é problema de configuração, não de sessão:
            // a mensagem precisa chegar ao usuário como está.
            Err(error @ SessionError::VaultUnavailable(_)) => Err(error),
            Err(_) => {
                self.logger.log("resposta de renovação incompleta");
                self.drop_session();
                Err(SessionError::AuthRequired)
            }
        }
    }

    async fn fetch_email(&self) -> Result<String, SessionError> {
        let response = self.send_authenticated(Method::Get, ME_PATH, None).await?;
        if !response.is_success() {
            return Err(SessionError::Api {
                status: response.status,
            });
        }
        let body = parse_json(&response.body)?;
        string_field(&body, "email")
    }

    /// Access na memória, refresh no cofre. Nada mais é guardado.
    fn persist_auth(
        &self,
        response: &HttpResponse,
        body: &serde_json::Value,
    ) -> Result<(), SessionError> {
        let access = string_field(body, "accessToken")?;
        let cookie = parse_refresh_cookie(&response.set_cookie).ok_or(SessionError::Malformed)?;
        let stored = StoredRefresh {
            cookie_name: cookie.name,
            value: cookie.value,
        };
        if let Err(error) = self.vault.save(&stored) {
            // Sem cofre não há sessão: o access não fica pendurado na memória.
            self.set_access(None);
            self.logger.log("cofre do sistema indisponível ao guardar a sessão");
            return Err(SessionError::from(error));
        }
        self.set_access(Some(access));
        Ok(())
    }
}

fn parse_json(raw: &str) -> Result<serde_json::Value, SessionError> {
    if raw.trim().is_empty() {
        return Ok(serde_json::Value::Null);
    }
    serde_json::from_str(raw).map_err(|_| SessionError::Malformed)
}

fn string_field(body: &serde_json::Value, field: &str) -> Result<String, SessionError> {
    body.get(field)
        .and_then(|v| v.as_str())
        .filter(|v| !v.is_empty())
        .map(|v| v.to_string())
        .ok_or(SessionError::Malformed)
}
