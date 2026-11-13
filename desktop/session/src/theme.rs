//! Preferência de tema do dispositivo (ADR-013, emendado pelo ADR-0029).
//!
//! Fica num arquivo de configuração do aplicativo. Não vai à API, não é
//! associada ao usuário e não entra em log nem telemetria. Também não vai ao
//! cofre do sistema: lá só mora o refresh de sessão.

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

pub const THEME_FILE: &str = "theme.json";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum ThemeMode {
    /// Acompanha o sistema operacional. Padrão sem escolha explícita.
    #[default]
    System,
    Light,
    Dark,
}

#[derive(Debug, thiserror::Error)]
pub enum ThemeError {
    #[error("não foi possível gravar a preferência de tema no dispositivo")]
    Write,
}

#[derive(Debug, Serialize, Deserialize)]
struct ThemeFile {
    theme: ThemeMode,
}

pub struct ThemeStore {
    path: PathBuf,
}

impl ThemeStore {
    /// `config_dir` é o diretório de configuração que o Tauri resolve por
    /// plataforma.
    pub fn new(config_dir: impl AsRef<Path>) -> Self {
        Self {
            path: config_dir.as_ref().join(THEME_FILE),
        }
    }

    /// Arquivo ausente, ilegível ou com valor desconhecido cai em `System`:
    /// uma preferência de apresentação nunca deve impedir o arranque.
    pub fn load(&self) -> ThemeMode {
        std::fs::read_to_string(&self.path)
            .ok()
            .and_then(|raw| serde_json::from_str::<ThemeFile>(&raw).ok())
            .map(|file| file.theme)
            .unwrap_or_default()
    }

    pub fn save(&self, mode: ThemeMode) -> Result<(), ThemeError> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent).map_err(|_| ThemeError::Write)?;
        }
        let raw = serde_json::to_string(&ThemeFile { theme: mode })
            .map_err(|_| ThemeError::Write)?;
        std::fs::write(&self.path, format!("{raw}\n")).map_err(|_| ThemeError::Write)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Diretório temporário próprio, para o teste não depender de crate extra.
    struct TempDir(PathBuf);

    impl TempDir {
        fn new(name: &str) -> Self {
            let path = std::env::temp_dir().join(format!(
                "verbas-theme-{name}-{}",
                std::process::id()
            ));
            let _ = std::fs::remove_dir_all(&path);
            std::fs::create_dir_all(&path).expect("cria temp");
            Self(path)
        }
    }

    impl Drop for TempDir {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn sem_escolha_previa_o_tema_segue_o_sistema() {
        let dir = TempDir::new("vazio");
        let store = ThemeStore::new(&dir.0);

        assert_eq!(store.load(), ThemeMode::System);
    }

    #[test]
    fn a_escolha_escura_sobrevive_ao_arranque_seguinte() {
        let dir = TempDir::new("escuro");

        ThemeStore::new(&dir.0).save(ThemeMode::Dark).expect("save");

        // Novo store = novo arranque do aplicativo.
        assert_eq!(ThemeStore::new(&dir.0).load(), ThemeMode::Dark);
    }

    #[test]
    fn a_escolha_pode_voltar_a_seguir_o_sistema() {
        let dir = TempDir::new("volta");
        let store = ThemeStore::new(&dir.0);

        store.save(ThemeMode::Light).expect("save claro");
        assert_eq!(store.load(), ThemeMode::Light);

        store.save(ThemeMode::System).expect("save sistema");
        assert_eq!(store.load(), ThemeMode::System);
    }

    #[test]
    fn arquivo_ilegivel_cai_no_sistema_sem_derrubar_o_arranque() {
        let dir = TempDir::new("corrompido");
        std::fs::write(dir.0.join(THEME_FILE), "isso não é json").expect("escreve");

        assert_eq!(ThemeStore::new(&dir.0).load(), ThemeMode::System);
    }

    #[test]
    fn o_arquivo_de_tema_guarda_so_o_tema() {
        let dir = TempDir::new("conteudo");
        ThemeStore::new(&dir.0).save(ThemeMode::Dark).expect("save");

        let raw = std::fs::read_to_string(dir.0.join(THEME_FILE)).expect("lê");

        assert_eq!(raw.trim(), r#"{"theme":"DARK"}"#);
        // Nenhum identificador de usuário ou segredo de sessão no arquivo.
        for proibido in ["email", "token", "refresh", "userId", "@"] {
            assert!(!raw.contains(proibido), "o arquivo de tema cita {proibido:?}");
        }
    }
}
