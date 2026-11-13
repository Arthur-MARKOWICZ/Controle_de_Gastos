//! Ponte IPC do cliente desktop (ADR-0029).
//!
//! Esta camada é deliberadamente fina: recebe o `invoke` do webview, chama o
//! adapter de sessão (crate `verbas-session`, sem Tauri) e devolve DTOs. Toda a
//! lógica testável mora no outro crate, que roda `cargo test` sem WebKitGTK.
//!
//! Os comandos são grosseiros de propósito — um `api` genérico em vez de um
//! comando por rota — para não multiplicar a superfície IPC.

use std::sync::Arc;
use tauri::Manager;
use verbas_session::http::{Method, ReqwestTransport};
use verbas_session::ipc::{ApiReplyView, AuthReply, ErrorView, RestoreReply, SessionView};
use verbas_session::logger::StderrLogger;
use verbas_session::session::{SessionAdapter, SessionError};
use verbas_session::theme::{ThemeMode, ThemeStore};
use verbas_session::vault::KeyringVault;

/// URL da API resolvida em tempo de build, igual ao bundle do webview.
const API_BASE_URL: &str = env!("API_BASE_URL");

type Adapter = SessionAdapter<ReqwestTransport, KeyringVault>;

struct AppState {
    adapter: Adapter,
    theme: ThemeStore,
}

/// Traduz o erro do adapter para o que o webview mostra. Nunca inclui token,
/// senha, código TOTP nem corpo de resposta de autenticação.
fn to_error_view(error: SessionError) -> ErrorView {
    let auth_required = matches!(error, SessionError::AuthRequired);
    ErrorView {
        message: error.to_string(),
        auth_required,
    }
}

#[tauri::command]
async fn login(
    state: tauri::State<'_, AppState>,
    email: String,
    password: String,
) -> Result<AuthReply, ErrorView> {
    state
        .adapter
        .login(&email, &password)
        .await
        .map(AuthReply::from)
        .map_err(to_error_view)
}

#[tauri::command]
async fn verify_mfa(
    state: tauri::State<'_, AppState>,
    challenge_id: String,
    code: String,
) -> Result<AuthReply, ErrorView> {
    state
        .adapter
        .verify_mfa(&challenge_id, &code)
        .await
        .map(AuthReply::from)
        .map_err(to_error_view)
}

#[tauri::command]
async fn restore(state: tauri::State<'_, AppState>) -> Result<RestoreReply, ErrorView> {
    state
        .adapter
        .restore()
        .await
        .map(|email| RestoreReply {
            session: email.map(|email| SessionView { email }),
        })
        .map_err(to_error_view)
}

#[tauri::command]
async fn logout(state: tauri::State<'_, AppState>) -> Result<(), ErrorView> {
    state.adapter.logout().await.map_err(to_error_view)
}

/// Chamada autenticada a uma rota de `/api/v1`. O corpo volta cru: a API é a
/// fonte das regras, o cliente só apresenta.
#[tauri::command]
async fn api(
    state: tauri::State<'_, AppState>,
    method: String,
    path: String,
    body: Option<String>,
) -> Result<ApiReplyView, ErrorView> {
    let method = match method.to_ascii_uppercase().as_str() {
        "GET" => Method::Get,
        "POST" => Method::Post,
        "PATCH" => Method::Patch,
        other => {
            return Err(ErrorView {
                message: format!("Método não suportado pelo cliente: {other}"),
                auth_required: false,
            })
        }
    };
    if !path.starts_with("/api/v1/") {
        return Err(ErrorView {
            message: "Caminho fora de /api/v1.".to_string(),
            auth_required: false,
        });
    }
    state
        .adapter
        .api(method, &path, body)
        .await
        .map(ApiReplyView::from)
        .map_err(to_error_view)
}

/// Tema do dispositivo (ADR-013). Não passa pela API em nenhum momento.
#[tauri::command]
fn theme_get(state: tauri::State<'_, AppState>) -> ThemeMode {
    state.theme.load()
}

#[tauri::command]
fn theme_set(state: tauri::State<'_, AppState>, mode: ThemeMode) -> Result<(), ErrorView> {
    state.theme.save(mode).map_err(|error| ErrorView {
        message: error.to_string(),
        auth_required: false,
    })
}

pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            let transport = ReqwestTransport::new(API_BASE_URL)
                .map_err(|error| Box::<dyn std::error::Error>::from(error.to_string()))?;
            let config_dir = app.path().app_config_dir()?;
            app.manage(AppState {
                adapter: SessionAdapter::new(
                    transport,
                    KeyringVault::new(),
                    Arc::new(StderrLogger),
                ),
                theme: ThemeStore::new(config_dir),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            login,
            verify_mfa,
            restore,
            logout,
            api,
            theme_get,
            theme_set
        ])
        .run(tauri::generate_context!())
        .expect("falha ao iniciar a janela do Verbas");
}
