//! Cofre do sistema operacional para o refresh de sessão (ADR-0029).
//!
//! Só o refresh mora aqui — valor e nome do cookie. O access JWT fica na
//! memória do processo e nunca é persistido, em lugar nenhum.
//!
//! Sem cofre disponível (Linux sem `secret-service` ativo, por exemplo), a
//! operação falha com [`VaultError::Unavailable`]. Não existe fallback para
//! arquivo em texto claro: é decisão do ADR, não omissão.

use serde::{Deserialize, Serialize};
use std::sync::Mutex;

/// O que vai para o cofre. Nenhum campo de access token, por construção.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StoredRefresh {
    pub cookie_name: String,
    pub value: String,
}

#[derive(Debug, thiserror::Error)]
pub enum VaultError {
    /// Não há cofre utilizável no sistema. A mensagem é de configuração.
    #[error("{0}")]
    Unavailable(String),
    /// O cofre existe mas recusou a operação.
    #[error("o cofre do sistema recusou a operação")]
    Refused,
    /// Havia uma entrada, mas não no formato esperado.
    #[error("a entrada do cofre está ilegível")]
    Corrupted,
}

pub trait SecretVault: Send + Sync {
    fn load(&self) -> Result<Option<StoredRefresh>, VaultError>;
    fn save(&self, refresh: &StoredRefresh) -> Result<(), VaultError>;
    fn clear(&self) -> Result<(), VaultError>;
}

/// Cofre em memória, para teste. Não persiste nada entre execuções.
#[derive(Default)]
pub struct MemoryVault {
    stored: Mutex<Option<StoredRefresh>>,
}

impl MemoryVault {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with(refresh: StoredRefresh) -> Self {
        Self {
            stored: Mutex::new(Some(refresh)),
        }
    }

    pub fn peek(&self) -> Option<StoredRefresh> {
        self.stored.lock().expect("vault mutex").clone()
    }
}

impl SecretVault for MemoryVault {
    fn load(&self) -> Result<Option<StoredRefresh>, VaultError> {
        Ok(self.peek())
    }

    fn save(&self, refresh: &StoredRefresh) -> Result<(), VaultError> {
        *self.stored.lock().expect("vault mutex") = Some(refresh.clone());
        Ok(())
    }

    fn clear(&self) -> Result<(), VaultError> {
        *self.stored.lock().expect("vault mutex") = None;
        Ok(())
    }
}

pub const VAULT_SERVICE: &str = "br.com.controlegastos.desktop";
pub const VAULT_ACCOUNT: &str = "refresh";

/// Mensagem de configuração mostrada quando o sistema não oferece cofre.
pub fn unavailable_message() -> String {
    if cfg!(target_os = "linux") {
        "Nenhum cofre de senhas disponível. Instale e habilite um serviço \
         compatível com secret-service (GNOME Keyring ou KWallet) e entre de novo. \
         O aplicativo não guarda a sessão em arquivo sem proteção."
            .to_string()
    } else {
        "Nenhum cofre de senhas disponível no sistema. Habilite o cofre da \
         plataforma e entre de novo. O aplicativo não guarda a sessão em arquivo \
         sem proteção."
            .to_string()
    }
}

/// Cofre real, sobre o `keyring` da plataforma.
pub struct KeyringVault {
    service: String,
    account: String,
}

impl Default for KeyringVault {
    fn default() -> Self {
        Self {
            service: VAULT_SERVICE.to_string(),
            account: VAULT_ACCOUNT.to_string(),
        }
    }
}

impl KeyringVault {
    pub fn new() -> Self {
        Self::default()
    }

    fn entry(&self) -> Result<keyring::Entry, VaultError> {
        keyring::Entry::new(&self.service, &self.account).map_err(map_keyring_error)
    }
}

fn map_keyring_error(error: keyring::Error) -> VaultError {
    match error {
        keyring::Error::PlatformFailure(_) | keyring::Error::NoStorageAccess(_) => {
            VaultError::Unavailable(unavailable_message())
        }
        keyring::Error::BadEncoding(_) | keyring::Error::Invalid(_, _) => VaultError::Corrupted,
        _ => VaultError::Refused,
    }
}

/// Roda `f` numa thread sem runtime tokio.
///
/// No Linux o `keyring` fala com o secret-service por zbus e faz `block_on`
/// internamente; dentro de um comando async do Tauri isso entra em pânico
/// ("Cannot start a runtime from within a runtime"). Uma thread própria por
/// chamada é barato aqui: o cofre só é tocado no login, renovação e logout.
fn off_runtime<T: Send>(f: impl FnOnce() -> T + Send) -> T {
    std::thread::scope(|scope| scope.spawn(f).join().expect("thread do cofre"))
}

impl SecretVault for KeyringVault {
    fn load(&self) -> Result<Option<StoredRefresh>, VaultError> {
        off_runtime(|| self.load_blocking())
    }

    fn save(&self, refresh: &StoredRefresh) -> Result<(), VaultError> {
        off_runtime(|| self.save_blocking(refresh))
    }

    fn clear(&self) -> Result<(), VaultError> {
        off_runtime(|| self.clear_blocking())
    }
}

impl KeyringVault {
    fn load_blocking(&self) -> Result<Option<StoredRefresh>, VaultError> {
        match self.entry()?.get_password() {
            Ok(raw) => serde_json::from_str(&raw)
                .map(Some)
                .map_err(|_| VaultError::Corrupted),
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(error) => Err(map_keyring_error(error)),
        }
    }

    fn save_blocking(&self, refresh: &StoredRefresh) -> Result<(), VaultError> {
        let raw = serde_json::to_string(refresh).map_err(|_| VaultError::Corrupted)?;
        self.entry()?
            .set_password(&raw)
            .map_err(map_keyring_error)
    }

    fn clear_blocking(&self) -> Result<(), VaultError> {
        match self.entry()?.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(error) => Err(map_keyring_error(error)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn o_que_vai_ao_cofre_nao_tem_campo_de_access_token() {
        let raw = serde_json::to_string(&StoredRefresh {
            cookie_name: "refresh_token".to_string(),
            value: "refresh-opaco".to_string(),
        })
        .expect("serializa");
        assert!(raw.contains("refresh_token"));
        assert!(!raw.to_lowercase().contains("access"));
    }

    #[test]
    fn round_trip_do_refresh_no_cofre_em_memoria() {
        let vault = MemoryVault::new();
        assert_eq!(vault.load().expect("load"), None);

        let refresh = StoredRefresh {
            cookie_name: "__Secure-refresh_token".to_string(),
            value: "valor-opaco".to_string(),
        };
        vault.save(&refresh).expect("save");
        assert_eq!(vault.load().expect("load"), Some(refresh));

        vault.clear().expect("clear");
        assert_eq!(vault.load().expect("load"), None);
    }

    /// O `keyring` no Linux faz `block_on` do zbus, que entra em pânico se a
    /// thread já tem um runtime tokio — caso dos comandos async do Tauri.
    #[tokio::test]
    async fn chamada_ao_cofre_roda_fora_do_runtime_tokio() {
        assert!(tokio::runtime::Handle::try_current().is_ok());
        let dentro_do_runtime = off_runtime(|| tokio::runtime::Handle::try_current().is_ok());
        assert!(!dentro_do_runtime);
    }

    #[test]
    fn a_mensagem_de_cofre_ausente_fala_de_configuracao() {
        let message = unavailable_message();
        assert!(message.to_lowercase().contains("cofre"));
        assert!(message.contains("sem proteção"));
    }
}
