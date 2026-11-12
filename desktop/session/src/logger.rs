//! Registro técnico do canal desktop.
//!
//! Regra do ADR-0029 e do inventário de privacidade: nada de senha, token,
//! código TOTP, valor ou descrição de gasto. O `Logger` é um ponto único por
//! onde tudo passa, justamente para o teste poder capturar a saída e provar a
//! ausência dos segredos.

use std::sync::Mutex;

pub trait Logger: Send + Sync {
    fn log(&self, line: &str);
}

/// Saída padrão do aplicativo: stderr, como o banner do MCP.
pub struct StderrLogger;

impl Logger for StderrLogger {
    fn log(&self, line: &str) {
        eprintln!("[verbas-desktop] {line}");
    }
}

/// Descarta tudo. Útil em teste de comportamento que não inspeciona log.
pub struct NullLogger;

impl Logger for NullLogger {
    fn log(&self, _line: &str) {}
}

/// Acumula as linhas para o teste de privacidade do log.
#[derive(Default)]
pub struct CollectingLogger {
    lines: Mutex<Vec<String>>,
}

impl CollectingLogger {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn lines(&self) -> Vec<String> {
        self.lines.lock().expect("log mutex").clone()
    }

    pub fn joined(&self) -> String {
        self.lines().join("\n")
    }
}

impl Logger for CollectingLogger {
    fn log(&self, line: &str) {
        self.lines.lock().expect("log mutex").push(line.to_string());
    }
}
