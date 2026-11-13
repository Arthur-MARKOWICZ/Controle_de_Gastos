use std::path::Path;

/// Resolve a URL da API no build do crate, pelas mesmas variáveis do
/// `vite.config.ts` (ADR-0029). URL ausente ou inválida derruba o build.
fn main() {
    let api_base_url = resolve_api_base_url();
    println!("cargo:rustc-env=API_BASE_URL={api_base_url}");
    println!("cargo:rerun-if-changed=../.env");
    println!("cargo:rerun-if-env-changed=API_ENV");
    println!("cargo:rerun-if-env-changed=API_BASE_URL_LOCAL");
    println!("cargo:rerun-if-env-changed=API_BASE_URL_PROD");

    tauri_build::build()
}

fn resolve_api_base_url() -> String {
    let from_file = read_env_file(Path::new("../.env"));
    let value = |key: &str| {
        std::env::var(key)
            .ok()
            .or_else(|| from_file.iter().find(|(k, _)| k == key).map(|(_, v)| v.clone()))
            .unwrap_or_default()
    };

    let env_name = value("API_ENV").trim().to_ascii_lowercase();
    let (key, raw) = match env_name.as_str() {
        "local" => ("API_BASE_URL_LOCAL", value("API_BASE_URL_LOCAL")),
        "prod" => ("API_BASE_URL_PROD", value("API_BASE_URL_PROD")),
        other => panic!(
            "API_ENV deve ser \"local\" ou \"prod\" (recebido: {other:?}). \
             Copie desktop/.env.example para desktop/.env."
        ),
    };

    let url = raw.trim().trim_end_matches('/').to_string();
    assert!(!url.is_empty(), "{key} é obrigatória");
    assert!(
        url.starts_with("http://") || url.starts_with("https://"),
        "{key} deve usar http ou https: {url}"
    );
    let rest = url.split_once("//").expect("protocolo").1;
    assert!(
        !rest.contains('/') && !rest.contains('?') && !rest.contains('#'),
        "{key} deve ser só a origem, sem caminho nem query: {url}"
    );
    url
}

/// Leitor mínimo de `.env`: `CHAVE=valor`, uma por linha, `#` comenta.
fn read_env_file(path: &Path) -> Vec<(String, String)> {
    let Ok(content) = std::fs::read_to_string(path) else {
        return Vec::new();
    };
    content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .filter_map(|line| line.split_once('='))
        .map(|(key, value)| {
            (
                key.trim().to_string(),
                value.trim().trim_matches('"').to_string(),
            )
        })
        .collect()
}
