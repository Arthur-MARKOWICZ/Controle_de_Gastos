//! Transporte HTTP do canal desktop.
//!
//! O trait existe para o adapter de sessão ser testável sem rede: o teste
//! injeta um transporte falso e inspeciona exatamente quais requisições saíram.

use std::future::Future;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Method {
    Get,
    Post,
    Patch,
    Put,
    Delete,
}

impl Method {
    pub fn as_str(self) -> &'static str {
        match self {
            Method::Get => "GET",
            Method::Post => "POST",
            Method::Patch => "PATCH",
            Method::Put => "PUT",
            Method::Delete => "DELETE",
        }
    }

    /// Lista fechada de métodos que o webview pode pedir; o resto é recusado.
    pub fn parse(raw: &str) -> Option<Method> {
        match raw.to_ascii_uppercase().as_str() {
            "GET" => Some(Method::Get),
            "POST" => Some(Method::Post),
            "PATCH" => Some(Method::Patch),
            "PUT" => Some(Method::Put),
            "DELETE" => Some(Method::Delete),
            _ => None,
        }
    }
}

#[derive(Debug, Clone)]
pub struct HttpRequest {
    pub method: Method,
    /// Caminho a partir da raiz, já com query. Ex.: `/api/v1/ledger/summary?month=2026-11`.
    pub path: String,
    pub headers: Vec<(String, String)>,
    pub body: Option<String>,
}

impl HttpRequest {
    pub fn new(method: Method, path: impl Into<String>) -> Self {
        Self {
            method,
            path: path.into(),
            headers: Vec::new(),
            body: None,
        }
    }

    pub fn header(mut self, name: &str, value: impl Into<String>) -> Self {
        self.headers.push((name.to_string(), value.into()));
        self
    }

    pub fn json_body(mut self, body: impl Into<String>) -> Self {
        self.body = Some(body.into());
        self.headers
            .push(("Content-Type".to_string(), "application/json".to_string()));
        self
    }

    pub fn header_value(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.as_str())
    }
}

#[derive(Debug, Clone)]
pub struct HttpResponse {
    pub status: u16,
    /// Todos os `Set-Cookie` da resposta, na ordem recebida.
    pub set_cookie: Vec<String>,
    pub body: String,
}

impl HttpResponse {
    pub fn new(status: u16, body: impl Into<String>) -> Self {
        Self {
            status,
            set_cookie: Vec::new(),
            body: body.into(),
        }
    }

    pub fn with_cookie(mut self, raw: impl Into<String>) -> Self {
        self.set_cookie.push(raw.into());
        self
    }

    pub fn is_success(&self) -> bool {
        (200..300).contains(&self.status)
    }
}

#[derive(Debug, thiserror::Error)]
pub enum HttpError {
    /// Falha de rede, DNS ou TLS. A mensagem não carrega corpo nem cabeçalho.
    #[error("falha de rede ao falar com a API")]
    Network,
}

pub trait HttpTransport: Send + Sync {
    fn send(
        &self,
        request: HttpRequest,
    ) -> impl Future<Output = Result<HttpResponse, HttpError>> + Send;
}

/// Transporte real, sobre `reqwest`. Vive no processo nativo: o webview nunca
/// faz a requisição, então o CORS de produção segue restrito à origem da web.
pub struct ReqwestTransport {
    client: reqwest::Client,
    api_base_url: String,
}

impl ReqwestTransport {
    /// `api_base_url` é a origem resolvida no build (ADR-0029), sem barra final.
    pub fn new(api_base_url: impl Into<String>) -> Result<Self, HttpError> {
        let client = reqwest::Client::builder()
            // O cookie de refresh é tratado à mão: nada de cookie jar implícito.
            .user_agent("verbas-desktop")
            .build()
            .map_err(|_| HttpError::Network)?;
        Ok(Self {
            client,
            api_base_url: api_base_url.into().trim_end_matches('/').to_string(),
        })
    }
}

impl HttpTransport for ReqwestTransport {
    fn send(
        &self,
        request: HttpRequest,
    ) -> impl Future<Output = Result<HttpResponse, HttpError>> + Send {
        let url = format!("{}{}", self.api_base_url, request.path);
        let method = match request.method {
            Method::Get => reqwest::Method::GET,
            Method::Post => reqwest::Method::POST,
            Method::Patch => reqwest::Method::PATCH,
            Method::Put => reqwest::Method::PUT,
            Method::Delete => reqwest::Method::DELETE,
        };
        let mut builder = self.client.request(method, url);
        for (name, value) in &request.headers {
            builder = builder.header(name, value);
        }
        if let Some(body) = request.body {
            builder = builder.body(body);
        }

        async move {
            let response = builder.send().await.map_err(|_| HttpError::Network)?;
            let status = response.status().as_u16();
            let set_cookie = response
                .headers()
                .get_all(reqwest::header::SET_COOKIE)
                .iter()
                .filter_map(|value| value.to_str().ok().map(str::to_string))
                .collect();
            let body = response.text().await.map_err(|_| HttpError::Network)?;
            Ok(HttpResponse {
                status,
                set_cookie,
                body,
            })
        }
    }
}

#[cfg(test)]
mod method_tests {
    use super::Method;

    #[test]
    fn parse_accepts_the_methods_the_api_uses_case_insensitively() {
        assert_eq!(Method::parse("get"), Some(Method::Get));
        assert_eq!(Method::parse("POST"), Some(Method::Post));
        assert_eq!(Method::parse("Patch"), Some(Method::Patch));
        assert_eq!(Method::parse("put"), Some(Method::Put));
        assert_eq!(Method::parse("DELETE"), Some(Method::Delete));
    }

    #[test]
    fn parse_rejects_methods_outside_the_allowlist() {
        for other in ["HEAD", "OPTIONS", "TRACE", "CONNECT", "", "GETX"] {
            assert_eq!(Method::parse(other), None, "{other} não deveria ser aceito");
        }
    }

    #[test]
    fn as_str_round_trips_with_parse() {
        for method in [Method::Get, Method::Post, Method::Patch, Method::Put, Method::Delete] {
            assert_eq!(Method::parse(method.as_str()), Some(method));
        }
    }
}
