//! Tipos que atravessam a ponte IPC até o webview (ADR-0029).
//!
//! Só e-mail e DTOs da API passam por aqui. Não existe campo de access token
//! nem de refresh: a ausência é verificada por teste, porque é uma garantia de
//! segurança, não um detalhe de serialização.

use crate::session::{ApiReply, AuthOutcome};
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
}
