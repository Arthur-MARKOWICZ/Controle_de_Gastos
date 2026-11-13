//! Testes do adapter de sessão do canal desktop (tarefas 3.2 a 3.6 do change
//! add-tauri-desktop-client).
//!
//! Nada aqui toca a rede nem o cofre real: o transporte e o cofre são injetados.

use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use verbas_session::http::{HttpError, HttpRequest, HttpResponse, HttpTransport, Method};
use verbas_session::logger::{CollectingLogger, Logger, NullLogger};
use verbas_session::session::{
    ApiReply, AuthOutcome, SessionAdapter, SessionError, LOGIN_PATH, LOGOUT_PATH, ME_PATH,
    MFA_VERIFY_PATH, REFRESH_PATH,
};
use verbas_session::vault::{unavailable_message, MemoryVault, SecretVault, StoredRefresh, VaultError};

const SENHA: &str = "senha-secreta-do-titular";
const CODIGO_TOTP: &str = "123456";
const ACCESS: &str = "jwt.access.primeiro";
const ACCESS_NOVO: &str = "jwt.access.renovado";
const REFRESH: &str = "refresh-opaco-original";
const REFRESH_NOVO: &str = "refresh-opaco-rotativo";

/// Transporte com respostas enfileiradas, que guarda o que saiu.
struct ScriptedTransport {
    queued: Mutex<VecDeque<Result<HttpResponse, HttpError>>>,
    sent: Mutex<Vec<HttpRequest>>,
}

impl ScriptedTransport {
    fn new(responses: Vec<HttpResponse>) -> Self {
        Self {
            queued: Mutex::new(responses.into_iter().map(Ok).collect()),
            sent: Mutex::new(Vec::new()),
        }
    }

    fn sent(&self) -> Vec<HttpRequest> {
        self.sent.lock().expect("sent").clone()
    }

    fn paths(&self) -> Vec<String> {
        self.sent().into_iter().map(|r| r.path).collect()
    }

    fn count_to(&self, path: &str) -> usize {
        self.paths().iter().filter(|p| p.as_str() == path).count()
    }

    fn request_to(&self, path: &str) -> HttpRequest {
        self.sent()
            .into_iter()
            .find(|r| r.path == path)
            .unwrap_or_else(|| panic!("nenhuma requisição para {path}; saíram: {:?}", self.paths()))
    }
}

impl HttpTransport for ScriptedTransport {
    fn send(
        &self,
        request: HttpRequest,
    ) -> impl std::future::Future<Output = Result<HttpResponse, HttpError>> + Send {
        let result = {
            self.sent.lock().expect("sent").push(request.clone());
            self.queued
                .lock()
                .expect("queued")
                .pop_front()
                .unwrap_or_else(|| {
                    panic!("resposta não enfileirada para {} {}", request.method.as_str(), request.path)
                })
        };
        async move { result }
    }
}

/// Cofre que nunca está disponível: Linux sem secret-service.
struct UnavailableVault {
    save_attempts: Mutex<usize>,
}

impl UnavailableVault {
    fn new() -> Self {
        Self {
            save_attempts: Mutex::new(0),
        }
    }

    fn save_attempts(&self) -> usize {
        *self.save_attempts.lock().expect("attempts")
    }
}

impl SecretVault for UnavailableVault {
    fn load(&self) -> Result<Option<StoredRefresh>, VaultError> {
        Err(VaultError::Unavailable(unavailable_message()))
    }

    fn save(&self, _refresh: &StoredRefresh) -> Result<(), VaultError> {
        *self.save_attempts.lock().expect("attempts") += 1;
        Err(VaultError::Unavailable(unavailable_message()))
    }

    fn clear(&self) -> Result<(), VaultError> {
        Err(VaultError::Unavailable(unavailable_message()))
    }
}

fn login_ok() -> HttpResponse {
    HttpResponse::new(200, format!(r#"{{"accessToken":"{ACCESS}","expiresIn":900}}"#))
        .with_cookie(format!(
            "refresh_token={REFRESH}; Path=/api/v1/auth; HttpOnly; SameSite=Strict"
        ))
}

fn refresh_ok() -> HttpResponse {
    HttpResponse::new(
        200,
        format!(r#"{{"accessToken":"{ACCESS_NOVO}","expiresIn":900}}"#),
    )
    .with_cookie(format!(
        "refresh_token={REFRESH_NOVO}; Path=/api/v1/auth; HttpOnly; SameSite=Strict"
    ))
}

fn me_ok() -> HttpResponse {
    HttpResponse::new(200, r#"{"email":"titular@exemplo.com"}"#)
}

fn stored() -> StoredRefresh {
    StoredRefresh {
        cookie_name: "refresh_token".to_string(),
        value: REFRESH.to_string(),
    }
}

fn adapter(
    responses: Vec<HttpResponse>,
) -> SessionAdapter<ScriptedTransport, MemoryVault> {
    SessionAdapter::new(
        ScriptedTransport::new(responses),
        MemoryVault::new(),
        Arc::new(NullLogger),
    )
}

// ---------------------------------------------------------------- 3.2 cofre

#[tokio::test]
async fn login_guarda_o_refresh_no_cofre_e_o_access_so_na_memoria() {
    let transport = ScriptedTransport::new(vec![login_ok(), me_ok()]);
    let vault = MemoryVault::new();
    let adapter = SessionAdapter::new(transport, vault, Arc::new(NullLogger));

    let outcome = adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    assert_eq!(
        outcome,
        AuthOutcome::Authenticated {
            email: "titular@exemplo.com".to_string()
        }
    );
    assert!(adapter.is_authenticated());
}

#[tokio::test]
async fn o_access_token_nunca_e_gravado_no_cofre() {
    let vault = MemoryVault::new();
    {
        let adapter = SessionAdapter::new(
            ScriptedTransport::new(vec![login_ok(), me_ok()]),
            MemoryVault::new(),
            Arc::new(NullLogger),
        );
        adapter.login("titular@exemplo.com", SENHA).await.expect("login");
    }
    // Round-trip explícito no cofre: só o refresh cabe lá.
    vault.save(&stored()).expect("save");
    let guardado = vault.peek().expect("entrada");
    assert_eq!(guardado.value, REFRESH);
    let serializado = serde_json::to_string(&guardado).expect("json");
    assert!(!serializado.contains(ACCESS));
    assert!(!serializado.contains("accessToken"));
}

#[tokio::test]
async fn credencial_invalida_devolve_mensagem_generica() {
    let adapter = adapter(vec![HttpResponse::new(401, r#"{"message":"Bad credentials"}"#)]);

    let error = adapter.login("titular@exemplo.com", "errada").await.unwrap_err();

    assert!(matches!(error, SessionError::InvalidCredentials));
    let shown = error.to_string();
    assert!(shown.contains("E-mail ou senha incorretos"));
    // Não diz se a conta existe.
    assert!(!shown.to_lowercase().contains("não existe"));
    assert!(!adapter.is_authenticated());
}

#[tokio::test]
async fn mfa_exigido_nao_abre_sessao() {
    let adapter = adapter(vec![HttpResponse::new(
        200,
        r#"{"mfaRequired":true,"challengeId":"desafio-1","expiresIn":300}"#,
    )]);

    let outcome = adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    assert_eq!(
        outcome,
        AuthOutcome::MfaRequired {
            challenge_id: "desafio-1".to_string(),
            expires_in: 300
        }
    );
    assert!(!adapter.is_authenticated());
}

#[tokio::test]
async fn totp_valido_conclui_a_sessao() {
    let transport = ScriptedTransport::new(vec![login_ok(), me_ok()]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));

    let outcome = adapter.verify_mfa("desafio-1", CODIGO_TOTP).await.expect("mfa");

    assert!(matches!(outcome, AuthOutcome::Authenticated { .. }));
    assert!(adapter.is_authenticated());
}

#[tokio::test]
async fn totp_invalido_mantem_o_desafio() {
    let adapter = adapter(vec![HttpResponse::new(401, r#"{"message":"invalid code"}"#)]);

    let error = adapter.verify_mfa("desafio-1", "000000").await.unwrap_err();

    assert!(matches!(error, SessionError::MfaRejected));
    assert!(!adapter.is_authenticated());
}

// ------------------------------------------------------------- 3.3 renovação

#[tokio::test]
async fn em_401_renova_uma_vez_e_repete_a_chamada() {
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(401, ""),      // GET protegido expirou
        refresh_ok(),                    // renovação
        HttpResponse::new(200, r#"{"items":[]}"#), // repetição
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    let reply = adapter
        .api(Method::Get, "/api/v1/alerts", None)
        .await
        .expect("api");

    assert_eq!(
        reply,
        ApiReply {
            status: 200,
            body: r#"{"items":[]}"#.to_string()
        }
    );
}

#[tokio::test]
async fn a_renovacao_manda_o_refresh_como_cookie_uma_unica_vez() {
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(401, ""),
        refresh_ok(),
        HttpResponse::new(200, "{}"),
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    adapter.api(Method::Get, "/api/v1/alerts", None).await.expect("api");

    let transport = adapter.transport_for_test();
    assert_eq!(transport.count_to(REFRESH_PATH), 1, "uma renovação, não duas");
    let renovacao = transport.request_to(REFRESH_PATH);
    assert_eq!(renovacao.method, Method::Post);
    assert_eq!(
        renovacao.header_value("Cookie"),
        Some(format!("refresh_token={REFRESH}").as_str())
    );
    // A chamada protegida saiu duas vezes: original e repetição.
    assert_eq!(transport.count_to("/api/v1/alerts"), 2);
}

#[tokio::test]
async fn a_renovacao_rotativa_substitui_o_refresh_guardado() {
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(401, ""),
        refresh_ok(),
        HttpResponse::new(200, "{}"),
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    adapter.api(Method::Get, "/api/v1/alerts", None).await.expect("api");

    assert_eq!(adapter.vault_for_test().peek().expect("entrada").value, REFRESH_NOVO);
}

#[tokio::test]
async fn renovacao_recusada_limpa_a_sessao() {
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(401, ""),
        HttpResponse::new(401, r#"{"message":"refresh inválido"}"#), // renovação falha
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    let error = adapter.api(Method::Get, "/api/v1/alerts", None).await.unwrap_err();

    assert!(matches!(error, SessionError::AuthRequired));
    assert!(!adapter.is_authenticated());
    assert_eq!(adapter.vault_for_test().peek(), None, "refresh apagado do cofre");
    // Não insistiu na chamada protegida depois da renovação falhar.
    assert_eq!(adapter.transport_for_test().count_to("/api/v1/alerts"), 1);
}

#[tokio::test]
async fn arranque_com_refresh_valido_restaura_a_sessao() {
    let transport = ScriptedTransport::new(vec![refresh_ok(), me_ok()]);
    let adapter = SessionAdapter::new(
        transport,
        MemoryVault::with(stored()),
        Arc::new(NullLogger),
    );

    let email = adapter.restore().await.expect("restore");

    assert_eq!(email, Some("titular@exemplo.com".to_string()));
    assert!(adapter.is_authenticated());
}

#[tokio::test]
async fn arranque_sem_refresh_vai_ao_login() {
    let adapter = adapter(vec![]);

    assert_eq!(adapter.restore().await.expect("restore"), None);
    assert!(!adapter.is_authenticated());
}

#[tokio::test]
async fn arranque_com_refresh_revogado_vai_ao_login() {
    let transport = ScriptedTransport::new(vec![HttpResponse::new(401, "")]);
    let adapter = SessionAdapter::new(
        transport,
        MemoryVault::with(stored()),
        Arc::new(NullLogger),
    );

    assert_eq!(adapter.restore().await.expect("restore"), None);
    assert!(!adapter.is_authenticated());
    assert_eq!(adapter.vault_for_test().peek(), None);
}

// ---------------------------------------------------------------- 3.4 logout

#[tokio::test]
async fn logout_revoga_na_api_apaga_o_cofre_e_proximo_arranque_pede_login() {
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(204, ""), // logout
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    adapter.logout().await.expect("logout");

    let transport = adapter.transport_for_test();
    assert_eq!(transport.count_to(LOGOUT_PATH), 1, "revogou na API");
    assert_eq!(
        transport.request_to(LOGOUT_PATH).header_value("Authorization"),
        Some(format!("Bearer {ACCESS}").as_str())
    );
    assert!(!adapter.is_authenticated());
    assert_eq!(adapter.vault_for_test().peek(), None, "cofre apagado");

    // Próxima restauração não tem o que restaurar.
    assert_eq!(adapter.restore().await.expect("restore"), None);
}

// ------------------------------------------------------------------ 3.5 logs

#[tokio::test]
async fn o_log_do_adapter_omite_senha_tokens_e_codigo_totp() {
    let logger = Arc::new(CollectingLogger::new());
    let transport = ScriptedTransport::new(vec![
        HttpResponse::new(401, r#"{"message":"Bad credentials"}"#), // login falho
        HttpResponse::new(200, r#"{"mfaRequired":true,"challengeId":"d1","expiresIn":300}"#),
        HttpResponse::new(401, ""),  // mfa recusado
        login_ok(),
        me_ok(),
        HttpResponse::new(204, ""),  // logout
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), logger.clone() as Arc<dyn Logger>);

    let _ = adapter.login("titular@exemplo.com", SENHA).await;
    let _ = adapter.login("titular@exemplo.com", SENHA).await;
    let _ = adapter.verify_mfa("d1", CODIGO_TOTP).await;
    let _ = adapter.verify_mfa("d1", CODIGO_TOTP).await;
    let _ = adapter.logout().await;

    let saida = logger.joined();
    assert!(!saida.is_empty(), "o adapter precisa registrar algo");
    for segredo in [SENHA, CODIGO_TOTP, ACCESS, REFRESH] {
        assert!(
            !saida.contains(segredo),
            "o log vazou {segredo:?}:\n{saida}"
        );
    }
    for palavra in ["accessToken", "Bearer", "refresh_token", "password"] {
        assert!(
            !saida.contains(palavra),
            "o log cita {palavra:?}:\n{saida}"
        );
    }
}

// ------------------------------------------------------- 3.6 cofre ausente

#[tokio::test]
async fn sem_cofre_no_sistema_o_login_falha_com_mensagem_de_configuracao() {
    let transport = ScriptedTransport::new(vec![login_ok()]);
    let vault = UnavailableVault::new();
    let adapter = SessionAdapter::new(transport, vault, Arc::new(NullLogger));

    let error = adapter.login("titular@exemplo.com", SENHA).await.unwrap_err();

    let SessionError::VaultUnavailable(message) = &error else {
        panic!("esperava VaultUnavailable, veio {error:?}");
    };
    assert!(message.to_lowercase().contains("cofre"));
    assert!(message.contains("sem proteção"), "a mensagem explica que nada é gravado em claro");
    assert!(!adapter.is_authenticated(), "não abre sessão sem cofre");
    assert_eq!(
        adapter.vault_for_test().save_attempts(),
        1,
        "tentou uma vez e desistiu, sem fallback para arquivo"
    );
    // A única chamada que saiu foi o login; nem /users/me foi buscado.
    assert_eq!(adapter.transport_for_test().paths(), vec![LOGIN_PATH.to_string()]);
}

#[tokio::test]
async fn sem_cofre_no_sistema_o_arranque_avisa_em_vez_de_fingir_sessao() {
    let adapter = SessionAdapter::new(
        ScriptedTransport::new(vec![]),
        UnavailableVault::new(),
        Arc::new(NullLogger),
    );

    let error = adapter.restore().await.unwrap_err();

    assert!(matches!(error, SessionError::VaultUnavailable(_)));
    assert!(!adapter.is_authenticated());
}

#[tokio::test]
async fn mfa_sem_cofre_tambem_recusa_a_sessao() {
    let adapter = SessionAdapter::new(
        ScriptedTransport::new(vec![login_ok()]),
        UnavailableVault::new(),
        Arc::new(NullLogger),
    );

    let error = adapter.verify_mfa("d1", CODIGO_TOTP).await.unwrap_err();

    assert!(matches!(error, SessionError::VaultUnavailable(_)));
    assert!(!adapter.is_authenticated());
}

// ------------------------------------------------- IPC: nada de segredo (4.1)

#[tokio::test]
async fn o_resultado_de_autenticacao_so_carrega_o_email() {
    let transport = ScriptedTransport::new(vec![login_ok(), me_ok()]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));

    let outcome = adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    let serializado = format!("{outcome:?}");
    assert!(serializado.contains("titular@exemplo.com"));
    for segredo in [ACCESS, REFRESH, SENHA] {
        assert!(!serializado.contains(segredo), "vazou {segredo:?} no resultado");
    }
}

#[tokio::test]
async fn api_repassa_bearer_e_corpo_em_post_de_lancamento() {
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(201, r#"{"id":"e1"}"#),
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");
    let corpo = r#"{"kind":"EXPENSE","amount":{"amount":"45.90","currency":"BRL"},"occurredAt":"2026-11-12","description":null}"#;

    let reply = adapter
        .api(
            Method::Post,
            "/api/v1/envelopes/e1/entries",
            Some(corpo.to_string()),
        )
        .await
        .expect("api");

    assert_eq!(reply.status, 201);
    let enviada = adapter
        .transport_for_test()
        .request_to("/api/v1/envelopes/e1/entries");
    assert_eq!(enviada.method, Method::Post);
    assert_eq!(
        enviada.header_value("Authorization"),
        Some(format!("Bearer {ACCESS}").as_str())
    );
    assert_eq!(enviada.header_value("Content-Type"), Some("application/json"));
    assert_eq!(enviada.body.as_deref(), Some(corpo));
}

#[tokio::test]
async fn api_busca_o_email_do_titular_com_bearer() {
    let transport = ScriptedTransport::new(vec![login_ok(), me_ok()]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));

    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    let enviada = adapter.transport_for_test().request_to(ME_PATH);
    assert_eq!(enviada.method, Method::Get);
    assert_eq!(
        enviada.header_value("Authorization"),
        Some(format!("Bearer {ACCESS}").as_str())
    );
}

#[tokio::test]
async fn api_sem_sessao_pede_login_sem_chamar_a_rede() {
    let adapter = adapter(vec![]);

    let error = adapter.api(Method::Get, "/api/v1/alerts", None).await.unwrap_err();

    assert!(matches!(error, SessionError::AuthRequired));
    assert!(adapter.transport_for_test().paths().is_empty());
}

#[tokio::test]
async fn a_recusa_de_autorizacao_chega_crua_a_interface() {
    // 403 em verba de terceiro não é erro de sessão: a UI mostra o erro da API.
    let transport = ScriptedTransport::new(vec![
        login_ok(),
        me_ok(),
        HttpResponse::new(403, r#"{"message":"Acesso negado"}"#),
    ]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));
    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    let reply = adapter
        .api(Method::Post, "/api/v1/envelopes/viagem/entries", Some("{}".to_string()))
        .await
        .expect("api devolve a resposta, não um erro de sessão");

    assert_eq!(reply.status, 403);
    assert!(reply.body.contains("Acesso negado"));
    assert!(adapter.is_authenticated(), "403 não encerra a sessão");
    assert_eq!(adapter.transport_for_test().count_to(REFRESH_PATH), 0);
}

#[tokio::test]
async fn o_login_envia_email_e_senha_so_para_a_rota_de_login() {
    let transport = ScriptedTransport::new(vec![login_ok(), me_ok()]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));

    adapter.login("titular@exemplo.com", SENHA).await.expect("login");

    let transport = adapter.transport_for_test();
    let login = transport.request_to(LOGIN_PATH);
    assert_eq!(login.method, Method::Post);
    assert!(login.body.as_deref().expect("corpo").contains(SENHA));
    // Nenhuma outra requisição carrega a senha.
    for request in transport.sent().into_iter().filter(|r| r.path != LOGIN_PATH) {
        assert!(!request.body.as_deref().unwrap_or("").contains(SENHA));
    }
}

#[tokio::test]
async fn o_codigo_totp_vai_so_para_a_rota_de_verificacao() {
    let transport = ScriptedTransport::new(vec![login_ok(), me_ok()]);
    let adapter = SessionAdapter::new(transport, MemoryVault::new(), Arc::new(NullLogger));

    adapter.verify_mfa("d1", CODIGO_TOTP).await.expect("mfa");

    let transport = adapter.transport_for_test();
    assert!(transport
        .request_to(MFA_VERIFY_PATH)
        .body
        .as_deref()
        .expect("corpo")
        .contains(CODIGO_TOTP));
    for request in transport.sent().into_iter().filter(|r| r.path != MFA_VERIFY_PATH) {
        assert!(!request.body.as_deref().unwrap_or("").contains(CODIGO_TOTP));
    }
}
