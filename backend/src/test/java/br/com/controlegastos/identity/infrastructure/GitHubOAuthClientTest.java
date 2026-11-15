package br.com.controlegastos.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.controlegastos.identity.application.OAuthProviderClient;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Respostas de erro, timeout e corpo inválido do provedor GitHub (ADR-0020). */
class GitHubOAuthClientTest {

    private static final String TOKEN = "https://github.com/login/oauth/access_token";
    private static final String USERINFO = "https://api.github.com/user";

    private MockRestServiceServer server;
    private GitHubOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubOAuthClient(builder.build(), "client-id", "client-secret", "https://api.example.com/");
    }

    @Test
    @DisplayName("monta a URL de autorização com client_id, redirect_uri e state")
    void montaUrlDeAutorizacao() {
        String url = client.authorizationUrl("estado-123");

        assertThat(url).startsWith("https://github.com/login/oauth/authorize")
                .contains("client_id=client-id")
                .contains("state=estado-123")
                .contains("redirect_uri=https://api.example.com/api/v1/auth/oauth/github/callback");
    }

    @Test
    @DisplayName("troca o código pelo access token enviando o formulário esperado")
    void trocaCodigoPorToken() {
        server.expect(once(), requestTo(TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=codigo%2Fbruto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=client-secret")))
                .andRespond(withSuccess("{\"access_token\":\"tok\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCode("codigo/bruto")).isEqualTo("tok");
        server.verify();
    }

    @Test
    @DisplayName("lê o perfil com o token Bearer")
    void leOPerfil() {
        server.expect(once(), requestTo(USERINFO))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"id\":7,\"email\":\"pessoa@example.com\"}", MediaType.APPLICATION_JSON));

        OAuthProviderClient.ProviderProfile profile = client.fetchProfile("tok");

        assertThat(profile.providerUserId()).isEqualTo("7");
        assertThat(profile.email()).isEqualTo("pessoa@example.com");
    }

    @Test
    @DisplayName("código recusado pelo provedor (400) lança HttpClientErrorException sem nova tentativa")
    void codigoRecusadoNaoRepete() {
        server.expect(once(), requestTo(TOKEN)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.exchangeCode("usado"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        server.verify();
    }

    @Test
    @DisplayName("provedor fora do ar na troca (5xx) lança HttpServerErrorException sem nova tentativa")
    void provedorForaNaTrocaNaoRepete() {
        server.expect(once(), requestTo(TOKEN)).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCode("codigo"))
                .isInstanceOf(HttpServerErrorException.InternalServerError.class);
        server.verify();
    }

    @Test
    @DisplayName("resposta sem access_token lança IllegalStateException")
    void respostaSemTokenFalha() {
        server.expect(once(), requestTo(TOKEN))
                .andRespond(withSuccess("{\"error\":\"bad_verification_code\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("codigo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GitHub não retornou um access token");
    }

    @Test
    @DisplayName("timeout na leitura do perfil tenta três vezes e lança ResourceAccessException")
    void timeoutNoPerfilTentaTresVezes() {
        server.expect(times(3), requestTo(USERINFO)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.fetchProfile("tok")).isInstanceOf(ResourceAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("perfil volta na segunda tentativa depois de falha transitória")
    void perfilRecuperaDepoisDeFalhaTransitoria() {
        server.expect(once(), requestTo(USERINFO)).andRespond(withServerError());
        server.expect(once(), requestTo(USERINFO))
                .andRespond(withSuccess("{\"id\":7,\"email\":\"pessoa@example.com\"}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfile("tok").providerUserId()).isEqualTo("7");
    }

    @Test
    @DisplayName("JSON inválido no perfil lança RestClientException")
    void jsonInvalidoNoPerfilFalha() {
        server.expect(times(3), requestTo(USERINFO))
                .andRespond(withSuccess("{nao e json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchProfile("tok")).isInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("perfil sem identificador lança IllegalStateException")
    void perfilSemIdentificadorFalha() {
        server.expect(once(), requestTo(USERINFO))
                .andRespond(withSuccess("{\"email\":\"pessoa@example.com\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchProfile("tok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GitHub não retornou um perfil válido");
    }

    @Test
    @DisplayName("perfil sem e-mail é devolvido com e-mail nulo para o serviço recusar")
    void perfilSemEmail() {
        server.expect(once(), requestTo(USERINFO))
                .andRespond(withSuccess("{\"id\":7,\"email\":null}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfile("tok").email()).isNull();
    }
}
