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
use verbas_session::http::ReqwestTransport;
use verbas_session::ipc::{api_request, ApiReplyView, AuthReply, ErrorView, RestoreReply, SessionView};
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

/// Conversão única, testada no crate de sessão.
fn to_error_view(error: SessionError) -> ErrorView {
    ErrorView::from(error)
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
    let method = api_request(&method, &path)?;
    state
        .adapter
        .api(method, &path, body)
        .await
        .map(ApiReplyView::from)
        .map_err(to_error_view)
}

/// Pede (se preciso) e informa a permissão de notificação do sistema (ADR-0033).
#[tauri::command]
fn reminder_permission(app: tauri::AppHandle) -> bool {
    use tauri_plugin_notification::{NotificationExt, PermissionState};
    let notification = app.notification();
    match notification.permission_state() {
        Ok(PermissionState::Granted) => true,
        _ => matches!(notification.request_permission(), Ok(PermissionState::Granted)),
    }
}

/// Mostra o lembrete diário. Texto fixo: nenhum dado financeiro na notificação.
#[tauri::command]
fn reminder_notify(app: tauri::AppHandle) -> Result<(), ErrorView> {
    use tauri_plugin_notification::NotificationExt;
    app.notification()
        .builder()
        .title("Nenhum lançamento hoje")
        .body("Abra o Verbas para registrar os gastos do dia.")
        .show()
        .map_err(|_| ErrorView {
            message: "Não foi possível mostrar a notificação.".to_string(),
            auth_required: false,
        })
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
        .plugin(tauri_plugin_notification::init())
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
            theme_set,
            reminder_permission,
            reminder_notify
        ])
        .run(tauri::generate_context!())
        .expect("falha ao iniciar a janela do Verbas");
}
