//! Tipos que atravessam a ponte IPC até o webview (ADR-0029).
//!
//! Só e-mail e DTOs da API passam por aqui. Não existe campo de access token
//! nem de refresh: a ausência é verificada por teste, porque é uma garantia de
//! segurança, não um detalhe de serialização.

use crate::http::Method;
use crate::session::{ApiReply, AuthOutcome, SessionError};
use serde::Serialize;

/// O que a interface sabe sobre a sessão.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct SessionView {
    pub email: String,
}

/// Resultado de `login` / `verify_mfa` visto pela interface.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "status", rename_all = "camelCase")]
pub enum AuthReply {
    #[serde(rename_all = "camelCase")]
    Authenticated { session: SessionView },
    /// `challengeId` é um identificador opaco de desafio, não um segredo de
    /// sessão: sem ele a interface não consegue concluir o segundo fator.
    #[serde(rename_all = "camelCase")]
    MfaRequired { challenge_id: String, expires_in: u64 },
}

impl From<AuthOutcome> for AuthReply {
    fn from(outcome: AuthOutcome) -> Self {
        match outcome {
            AuthOutcome::Authenticated { email } => AuthReply::Authenticated {
                session: SessionView { email },
            },
            AuthOutcome::MfaRequired {
                challenge_id,
                expires_in,
            } => AuthReply::MfaRequired {
                challenge_id,
                expires_in,
            },
        }
    }
}

/// Resultado de `restore`: `None` manda a interface à tela de login.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreReply {
    pub session: Option<SessionView>,
}

/// Resposta crua de uma rota de recurso. O corpo é o JSON da API, repassado
/// sem interpretação: regra financeira nenhuma mora no cliente.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ApiReplyView {
    pub status: u16,
    pub body: String,
}

impl From<ApiReply> for ApiReplyView {
    fn from(reply: ApiReply) -> Self {
        Self {
            status: reply.status,
            body: reply.body,
        }
    }
}

/// Erro devolvido ao webview: só a mensagem já pronta para leitura.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ErrorView {
    pub message: String,
    /// Marca os erros que levam a interface de volta ao login.
    pub auth_required: bool,
}

/// Traduz o erro do adapter para o que o webview mostra. Nunca inclui token,
/// senha, código TOTP nem corpo de resposta de autenticação.
impl From<SessionError> for ErrorView {
    fn from(error: SessionError) -> Self {
        let auth_required = matches!(error, SessionError::AuthRequired);
        ErrorView {
            message: error.to_string(),
            auth_required,
        }
    }
}

fn refused(message: String) -> ErrorView {
    ErrorView {
        message,
        auth_required: false,
    }
}

/// Valida o pedido do comando `api` antes de qualquer rede: método da lista
/// fechada e caminho dentro de `/api/v1/`.
pub fn api_request(method: &str, path: &str) -> Result<Method, ErrorView> {
    let Some(parsed) = Method::parse(method) else {
        return Err(refused(format!("Método não suportado pelo cliente: {method}")));
    };
    if !path.starts_with("/api/v1/") || escapes_api_prefix(path) {
        return Err(refused("Caminho fora de /api/v1.".to_string()));
    }
    Ok(parsed)
}

/// A URL resolve `.` e `..` (também em `%2e`) antes do envio; um segmento
/// assim, ou uma barra invertida, tiraria a chamada de `/api/v1/` com o token.
fn escapes_api_prefix(path: &str) -> bool {
    let route = path.split(['?', '#']).next().unwrap_or_default();
    route.contains('\\')
        || route.split('/').any(|segment| {
            let decoded = segment.to_ascii_lowercase().replace("%2e", ".");
            decoded == "." || decoded == ".."
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    const ACCESS: &str = "jwt.access.secreto";
    const REFRESH: &str = "refresh-opaco-secreto";

    #[test]
    fn a_resposta_de_autenticacao_nao_tem_campo_de_token() {
        let json = serde_json::to_string(&AuthReply::from(AuthOutcome::Authenticated {
            email: "titular@exemplo.com".to_string(),
        }))
        .expect("json");

        assert!(json.contains("titular@exemplo.com"));
        for proibido in ["accessToken", "access_token", "refresh", "token", "cookie"] {
            assert!(
                !json.to_lowercase().contains(&proibido.to_lowercase()),
                "a resposta IPC cita {proibido:?}: {json}"
            );
        }
    }

    #[test]
    fn o_desafio_mfa_nao_carrega_sessao() {
        let json = serde_json::to_string(&AuthReply::from(AuthOutcome::MfaRequired {
            challenge_id: "desafio-1".to_string(),
            expires_in: 300,
        }))
        .expect("json");

        assert!(json.contains("desafio-1"));
        assert!(!json.contains(ACCESS));
        assert!(!json.contains(REFRESH));
        assert!(!json.to_lowercase().contains("accesstoken"));
    }

    #[test]
    fn a_restauracao_sem_sessao_serializa_nulo() {
        let json = serde_json::to_string(&RestoreReply { session: None }).expect("json");
        assert_eq!(json, r#"{"session":null}"#);
    }

    #[test]
    fn a_restauracao_com_sessao_so_leva_o_email() {
        let json = serde_json::to_string(&RestoreReply {
            session: Some(SessionView {
                email: "titular@exemplo.com".to_string(),
            }),
        })
        .expect("json");
        assert_eq!(json, r#"{"session":{"email":"titular@exemplo.com"}}"#);
    }

    #[test]
    fn a_resposta_de_recurso_repassa_status_e_corpo_sem_token() {
        let json = serde_json::to_string(&ApiReplyView::from(crate::session::ApiReply {
            status: 200,
            body: r#"{"income":{"amount":"8000.00"}}"#.to_string(),
        }))
        .expect("json");

        assert!(json.contains("8000.00"));
        assert!(!json.contains(ACCESS));
        assert!(!json.to_lowercase().contains("bearer"));
    }

    #[test]
    fn o_erro_leva_mensagem_e_marca_de_relogin() {
        let json = serde_json::to_string(&ErrorView {
            message: "Sessão expirada. Entre de novo.".to_string(),
            auth_required: true,
        })
        .expect("json");
        assert_eq!(
            json,
            r#"{"message":"Sessão expirada. Entre de novo.","authRequired":true}"#
        );
    }

    #[test]
    fn sessao_expirada_marca_o_erro_para_voltar_ao_login() {
        let view = ErrorView::from(SessionError::AuthRequired);

        assert_eq!(view.message, "Sessão expirada. Entre de novo.");
        assert!(view.auth_required);
    }

    #[test]
    fn credencial_errada_nao_revela_se_a_conta_existe_nem_pede_relogin() {
        let view = ErrorView::from(SessionError::InvalidCredentials);

        assert_eq!(view.message, "E-mail ou senha incorretos.");
        assert!(!view.auth_required);
    }

    #[test]
    fn erros_de_rede_e_de_api_viram_texto_sem_relogin() {
        assert_eq!(
            ErrorView::from(SessionError::Api { status: 422 }),
            ErrorView { message: "A API recusou a operação (HTTP 422).".to_string(), auth_required: false }
        );
        assert!(!ErrorView::from(SessionError::Network).auth_required);
        assert!(!ErrorView::from(SessionError::MfaRejected).auth_required);
    }

    #[test]
    fn pedido_valido_devolve_o_metodo() {
        assert_eq!(api_request("get", "/api/v1/ledger/summary?month=2026-11"), Ok(Method::Get));
        assert_eq!(api_request("DELETE", "/api/v1/envelopes/e-1/participants/u-1"), Ok(Method::Delete));
    }

    #[test]
    fn metodo_fora_da_lista_e_recusado_com_a_mensagem() {
        assert_eq!(
            api_request("TRACE", "/api/v1/alerts"),
            Err(refused("Método não suportado pelo cliente: TRACE".to_string()))
        );
    }

    #[test]
    fn caminho_fora_da_api_e_recusado() {
        for path in ["/actuator/env", "api/v1/alerts", "//evil.example.com/api/v1/x", "/api/v1", ""] {
            assert_eq!(
                api_request("GET", path),
                Err(refused("Caminho fora de /api/v1.".to_string())),
                "{path:?} deveria ser recusado"
            );
        }
    }

    #[test]
    fn a_url_montada_resolveria_ponto_ponto_para_fora_da_api() {
        // Por isso o caminho precisa ser recusado antes: o token iria junto.
        let url = reqwest::Url::parse("http://localhost:8080/api/v1/../../actuator/env").expect("url");
        assert_eq!(url.path(), "/actuator/env");
        let encoded = reqwest::Url::parse("http://localhost:8080/api/v1/%2e%2e/%2E%2E/actuator").expect("url");
        assert_eq!(encoded.path(), "/actuator");
    }

    #[test]
    fn segmento_ponto_ponto_e_recusado_mesmo_com_o_prefixo_certo() {
        for path in [
            "/api/v1/../../actuator/env",
            "/api/v1/%2e%2e/%2e%2e/actuator",
            "/api/v1/%2E%2E/actuator",
            "/api/v1/.%2e/actuator",
            "/api/v1/./alerts",
            "/api/v1/..\\..\\actuator",
        ] {
            assert_eq!(
                api_request("GET", path),
                Err(refused("Caminho fora de /api/v1.".to_string())),
                "{path:?} deveria ser recusado"
            );
        }
    }

    #[test]
    fn ponto_ponto_na_query_nao_e_segmento_de_caminho() {
        assert_eq!(api_request("GET", "/api/v1/ledger/history?description=a..b"), Ok(Method::Get));
    }
}
