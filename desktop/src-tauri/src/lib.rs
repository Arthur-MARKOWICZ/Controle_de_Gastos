//! Ponte IPC do cliente desktop (ADR-0029).
//!
//! Esta camada é deliberadamente fina: recebe o `invoke` do webview, chama o
//! adapter de sessão (crate `verbas-session`, sem Tauri) e devolve DTOs. Toda a
//! lógica testável mora no outro crate, que roda `cargo test` sem WebKitGTK.
//!
//! Os comandos são grosseiros de propósito — um `api` genérico em vez de um
//! comando por rota — para não multiplicar a superfície IPC.

use std::sync::Arc;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Emitter, Manager};
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut, ShortcutState};
use verbas_session::http::ReqwestTransport;
use verbas_session::ipc::{api_request, ApiReplyView, AuthReply, ErrorView, RestoreReply, SessionView};
use verbas_session::logger::StderrLogger;
use verbas_session::session::{SessionAdapter, SessionError};
use verbas_session::theme::{ThemeMode, ThemeStore};
use verbas_session::vault::KeyringVault;

/// URL da API resolvida em tempo de build, igual ao bundle do webview.
const API_BASE_URL: &str = env!("API_BASE_URL");

/// Atalho global padrão no Linux (design D4 / spec ui-desktop/system-launch).
const GLOBAL_SHORTCUT: &str = "Ctrl+Alt+G";

/// Evento consumido pelo webview para abrir o diálogo de captura.
const OPEN_QUICK_EXPENSE_EVENT: &str = "open-quick-expense";

type Adapter = SessionAdapter<ReqwestTransport, KeyringVault>;

struct AppState {
    adapter: Adapter,
    theme: ThemeStore,
}

/// Conversão única, testada no crate de sessão.
fn to_error_view(error: SessionError) -> ErrorView {
    ErrorView::from(error)
}

fn show_main_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn emit_open_quick_expense(app: &tauri::AppHandle) {
    show_main_window(app);
    let _ = app.emit(OPEN_QUICK_EXPENSE_EVENT, ());
}

/// Ação do menu da bandeja (testável sem WebKitGTK).
#[derive(Debug, PartialEq, Eq)]
enum TrayMenuAction {
    OpenQuickExpense,
    ShowWindow,
    Quit,
    Ignore,
}

fn tray_menu_action(id: &str) -> TrayMenuAction {
    match id {
        "new_expense" => TrayMenuAction::OpenQuickExpense,
        "show" => TrayMenuAction::ShowWindow,
        "quit" => TrayMenuAction::Quit,
        _ => TrayMenuAction::Ignore,
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

/// Autostart no login da sessão (default desligado; não envia à API).
#[tauri::command]
fn autostart_get(app: tauri::AppHandle) -> Result<bool, ErrorView> {
    use tauri_plugin_autostart::ManagerExt;
    app.autolaunch().is_enabled().map_err(|_| ErrorView {
        message: "Não foi possível ler a preferência de início automático.".to_string(),
        auth_required: false,
    })
}

#[tauri::command]
fn autostart_set(app: tauri::AppHandle, enabled: bool) -> Result<(), ErrorView> {
    use tauri_plugin_autostart::ManagerExt;
    let launcher = app.autolaunch();
    let result = if enabled {
        launcher.enable()
    } else {
        launcher.disable()
    };
    result.map_err(|_| ErrorView {
        message: "Não foi possível atualizar o início automático.".to_string(),
        auth_required: false,
    })
}

fn setup_tray(app: &tauri::AppHandle) -> tauri::Result<()> {
    let show = MenuItem::with_id(app, "show", "Mostrar janela", true, None::<&str>)?;
    let new_expense = MenuItem::with_id(app, "new_expense", "Novo gasto", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Sair", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&new_expense, &show, &quit])?;

    let icon = app
        .default_window_icon()
        .cloned()
        .expect("ícone padrão do bundle");

    let app_handle = app.clone();
    TrayIconBuilder::with_id("verbas-tray")
        .icon(icon)
        .menu(&menu)
        .tooltip("Verbas")
        .on_menu_event(move |app, event| match tray_menu_action(event.id.as_ref()) {
            TrayMenuAction::OpenQuickExpense => emit_open_quick_expense(app),
            TrayMenuAction::ShowWindow => show_main_window(app),
            TrayMenuAction::Quit => app.exit(0),
            TrayMenuAction::Ignore => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                show_main_window(tray.app_handle());
            }
        })
        .build(app)?;

    // Mantém a handle viva no estado do app via clone acima.
    let _ = app_handle;
    Ok(())
}

fn setup_global_shortcut(app: &tauri::AppHandle) -> Result<(), String> {
    let shortcut = Shortcut::new(Some(Modifiers::CONTROL | Modifiers::ALT), Code::KeyG);
    let app_handle = app.clone();
    app.global_shortcut()
        .on_shortcut(shortcut, move |_app, _shortcut, event| {
            if event.state == ShortcutState::Pressed {
                emit_open_quick_expense(&app_handle);
            }
        })
        .map_err(|error| error.to_string())?;
    let _ = GLOBAL_SHORTCUT;
    Ok(())
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_autostart::Builder::new().build())
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
            setup_tray(app.handle())?;
            if let Err(error) = setup_global_shortcut(app.handle()) {
                // Wayland/X11 frágil: degrada sem derrubar o app (design Risks).
                eprintln!("atalho global indisponível: {error}");
            }
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
            autostart_get,
            autostart_set,
            reminder_permission,
            reminder_notify
        ])
        .run(tauri::generate_context!())
        .expect("falha ao iniciar a janela do Verbas");
}

#[cfg(test)]
mod system_launch_tests {
    use super::*;

    #[test]
    fn atalho_global_padrao_e_ctrl_alt_g() {
        assert_eq!(GLOBAL_SHORTCUT, "Ctrl+Alt+G");
        assert_eq!(OPEN_QUICK_EXPENSE_EVENT, "open-quick-expense");
    }

    #[test]
    fn menu_da_bandeja_mapeia_novo_gasto_e_mostrar() {
        assert_eq!(tray_menu_action("new_expense"), TrayMenuAction::OpenQuickExpense);
        assert_eq!(tray_menu_action("show"), TrayMenuAction::ShowWindow);
        assert_eq!(tray_menu_action("quit"), TrayMenuAction::Quit);
        assert_eq!(tray_menu_action("other"), TrayMenuAction::Ignore);
    }
}
