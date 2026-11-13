//! Sessão do cliente desktop (ADR-0029).
//!
//! Este crate não depende de Tauri: todo o HTTP autenticado, a leitura do
//! cookie de refresh e o acesso ao cofre do sistema moram aqui, para poderem
//! ser testados sem o toolchain de GUI.
//!
//! Nenhuma regra financeira vive neste crate. A API é a fonte de renda, saldo,
//! carry e autorização; aqui só há transporte e sessão.

pub mod cookies;
pub mod http;
pub mod ipc;
pub mod logger;
pub mod session;
pub mod theme;
pub mod vault;
