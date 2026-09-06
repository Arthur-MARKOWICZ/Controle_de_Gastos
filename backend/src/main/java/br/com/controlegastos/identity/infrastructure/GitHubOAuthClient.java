package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.application.OAuthProviderClient;
import br.com.controlegastos.identity.domain.OAuthProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component("githubOAuthClient")
public class GitHubOAuthClient implements OAuthProviderClient {

    private static final String AUTHORIZATION_ENDPOINT = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_ENDPOINT = "https://github.com/login/oauth/access_token";
    private static final String USERINFO_ENDPOINT = "https://api.github.com/user";
    private static final int PROFILE_FETCH_ATTEMPTS = 3;

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GitHubOAuthClient(
            @Value("${app.oauth.github.client-id}") String clientId,
            @Value("${app.oauth.github.client-secret}") String clientSecret,
            @Value("${app.oauth.callback-base-url}") String callbackBaseUrl
    ) {
        this.restClient = RestClient.create();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = callbackBaseUrl.replaceAll("/+$", "") + "/api/v1/auth/oauth/github/callback";
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GITHUB;
    }

    @Override
    public String authorizationUrl(String rawState) {
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", rawState)
                .build()
                .toUriString();
    }

    @Override
    public String exchangeCode(String code) {
        // Troca de authorization code por token: uma única tentativa, nunca retry
        // (o code é de uso único; reenviar após o provider já ter processado falharia por engano).
        TokenResponse response = restClient.post()
                .uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Accept", "application/json")
                .body(tokenRequestBody(code))
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("GitHub não retornou um access token");
        }
        return response.accessToken();
    }

    @Override
    public ProviderProfile fetchProfile(String accessToken) {
        RestClientException lastFailure = null;
        for (int attempt = 0; attempt < PROFILE_FETCH_ATTEMPTS; attempt++) {
            try {
                UserInfoResponse info = restClient.get()
                        .uri(USERINFO_ENDPOINT)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/vnd.github+json")
                        .retrieve()
                        .body(UserInfoResponse.class);
                if (info == null || info.id() == null) {
                    throw new IllegalStateException("GitHub não retornou um perfil válido");
                }
                // Só usa o e-mail já público em GET /user; não consulta /user/emails
                // quando ele vier nulo (decisão registrada no ADR-020).
                return new ProviderProfile(String.valueOf(info.id()), info.email());
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private String tokenRequestBody(String code) {
        return "code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(redirectUri);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record UserInfoResponse(@JsonProperty("id") Long id, @JsonProperty("email") String email) {
    }
}
