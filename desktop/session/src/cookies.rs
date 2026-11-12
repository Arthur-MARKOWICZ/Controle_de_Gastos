//! Extração do cookie de refresh emitido por `/auth/login`, `/auth/refresh` e
//! `/auth/mfa/verify`. Espelha `mcp/src/cookies.ts`.

/// Nome e valor do cookie de refresh. O access token nunca passa por aqui: ele
/// vem no corpo JSON da resposta.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RefreshCookie {
    pub name: String,
    pub value: String,
}

/// Lê o primeiro `Set-Cookie` que carrega um refresh.
///
/// Um cookie de valor vazio (como o que a API manda no logout para expirar a
/// entrada) não conta como refresh.
pub fn parse_refresh_cookie(set_cookie_headers: &[String]) -> Option<RefreshCookie> {
    for header in set_cookie_headers {
        let pair = header.split(';').next().unwrap_or("");
        let (name, value) = match pair.split_once('=') {
            Some((name, value)) => (name.trim(), value.trim()),
            None => continue,
        };
        if name.is_empty() || value.is_empty() {
            continue;
        }
        if is_refresh_cookie_name(name) {
            return Some(RefreshCookie {
                name: name.to_string(),
                value: value.to_string(),
            });
        }
    }
    None
}

fn is_refresh_cookie_name(name: &str) -> bool {
    name.to_ascii_lowercase().contains("refresh")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn headers(values: &[&str]) -> Vec<String> {
        values.iter().map(|v| v.to_string()).collect()
    }

    #[test]
    fn extrai_nome_e_valor_do_refresh() {
        let found = parse_refresh_cookie(&headers(&[
            "refresh_token=abc123; Path=/api/v1/auth; HttpOnly; SameSite=Strict",
        ]));
        assert_eq!(
            found,
            Some(RefreshCookie {
                name: "refresh_token".to_string(),
                value: "abc123".to_string(),
            })
        );
    }

    #[test]
    fn aceita_o_prefixo_secure_de_producao() {
        let found = parse_refresh_cookie(&headers(&[
            "__Secure-refresh_token=xyz789; Path=/; Secure; HttpOnly; SameSite=Strict",
        ]));
        assert_eq!(found.unwrap().name, "__Secure-refresh_token");
    }

    #[test]
    fn ignora_cookies_que_nao_sao_de_refresh() {
        let found = parse_refresh_cookie(&headers(&[
            "XSRF-TOKEN=nada; Path=/",
            "session_hint=1; Path=/",
        ]));
        assert_eq!(found, None);
    }

    #[test]
    fn escolhe_o_refresh_entre_varios_cookies() {
        let found = parse_refresh_cookie(&headers(&[
            "XSRF-TOKEN=nada; Path=/",
            "refresh_token=valor-certo; Path=/api/v1/auth; HttpOnly",
        ]));
        assert_eq!(found.unwrap().value, "valor-certo");
    }

    #[test]
    fn ignora_refresh_com_valor_vazio() {
        // É assim que a API expira o cookie no logout.
        let found = parse_refresh_cookie(&headers(&[
            "refresh_token=; Path=/api/v1/auth; Max-Age=0",
        ]));
        assert_eq!(found, None);
    }

    #[test]
    fn sem_cabecalho_nenhum_nao_ha_refresh() {
        assert_eq!(parse_refresh_cookie(&[]), None);
    }

    #[test]
    fn o_access_token_nao_vem_do_set_cookie() {
        // A API manda o access no corpo JSON; se algum dia aparecer num cookie,
        // este parser não deve confundi-lo com o refresh.
        let found = parse_refresh_cookie(&headers(&["access_token=jwt.aqui; Path=/"]));
        assert_eq!(found, None);
    }
}
