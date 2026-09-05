package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.application.OAuthProviderClient;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.infrastructure.IdentityProviderLinkRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OAuthApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.cookie-name", () -> "refresh_token");
        registry.add("app.oauth.web-base-url", () -> "https://app.example.com");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository users;

    @Autowired
    IdentityProviderLinkRepository links;

    @MockitoBean
    OAuthProviderClient googleOAuthClient;

    @MockitoBean
    OAuthProviderClient githubOAuthClient;

    @Test
    void authorizeUrlReturnsTheUrlBuiltByTheProviderClient() throws Exception {
        when(googleOAuthClient.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(googleOAuthClient.authorizationUrl(any()))
                .thenReturn("https://accounts.google.com/authorize?state=abc");

        mockMvc.perform(post("/api/v1/auth/oauth/google/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl")
                        .value("https://accounts.google.com/authorize?state=abc"));
    }

    @Test
    void callbackForANewProviderIdentityCreatesAnAccountAndRedirectsWithTheSessionCookie() throws Exception {
        when(googleOAuthClient.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(googleOAuthClient.authorizationUrl(any()))
                .thenAnswer(invocation -> "https://accounts.google.com/authorize?state=" + invocation.getArgument(0));
        when(googleOAuthClient.exchangeCode("valid-code")).thenReturn("access-token");
        when(googleOAuthClient.fetchProfile("access-token"))
                .thenReturn(new OAuthProviderClient.ProviderProfile("google-42", "nova.pessoa@example.com"));

        String authorizeUrlResponse = mockMvc.perform(post("/api/v1/auth/oauth/google/authorize-url"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(authorizeUrlResponse).get("authorizationUrl").asText();

        mockMvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "valid-code")
                        .param("state", extractState(url)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/oauth/callback?status=ok"))
                .andExpect(cookie().exists("refresh_token"));

        assertThat(users.findByEmailNormalized("nova.pessoa@example.com")).isPresent();
    }

    @Test
    void callbackForANewGitHubIdentityCreatesAnAccountAndRedirectsWithTheSessionCookie() throws Exception {
        when(githubOAuthClient.provider()).thenReturn(OAuthProvider.GITHUB);
        when(githubOAuthClient.authorizationUrl(any()))
                .thenAnswer(invocation -> "https://github.com/login/oauth/authorize?state=" + invocation.getArgument(0));
        when(githubOAuthClient.exchangeCode("valid-code")).thenReturn("access-token");
        when(githubOAuthClient.fetchProfile("access-token"))
                .thenReturn(new OAuthProviderClient.ProviderProfile("github-7", "nova.pessoa.gh@example.com"));

        String authorizeUrlResponse = mockMvc.perform(post("/api/v1/auth/oauth/github/authorize-url"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(authorizeUrlResponse).get("authorizationUrl").asText();

        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "valid-code")
                        .param("state", extractState(url)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/oauth/callback?status=ok"))
                .andExpect(cookie().exists("refresh_token"));

        assertThat(users.findByEmailNormalized("nova.pessoa.gh@example.com")).isPresent();
    }

    @Test
    void callbackFailsGenericallyWhenGitHubDoesNotReturnAnEmail() throws Exception {
        when(githubOAuthClient.provider()).thenReturn(OAuthProvider.GITHUB);
        when(githubOAuthClient.authorizationUrl(any()))
                .thenAnswer(invocation -> "https://github.com/login/oauth/authorize?state=" + invocation.getArgument(0));
        when(githubOAuthClient.exchangeCode("valid-code")).thenReturn("access-token");
        when(githubOAuthClient.fetchProfile("access-token"))
                .thenReturn(new OAuthProviderClient.ProviderProfile("github-8", null));

        String authorizeUrlResponse = mockMvc.perform(post("/api/v1/auth/oauth/github/authorize-url"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(authorizeUrlResponse).get("authorizationUrl").asText();

        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "valid-code")
                        .param("state", extractState(url)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/oauth/callback?error=oauth_failed"));
    }

    @Test
    void callbackFailsGenericallyWhenTheStateIsMissingOrInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "any-code")
                        .param("state", "invalid-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/oauth/callback?error=oauth_failed"));
    }

    @Test
    void callbackFailsGenericallyWhenTheProviderIsUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/does-not-exist/callback")
                        .param("code", "any-code")
                        .param("state", "any-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/oauth/callback?error=oauth_failed"));
    }

    @Test
    void connectingAProviderToAnAuthenticatedAccountLinksItAndRedirectsToSecurityWithoutASessionCookie()
            throws Exception {
        String accessToken = registerAndLogin("conecta1@example.com");
        when(googleOAuthClient.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(googleOAuthClient.authorizationUrl(any()))
                .thenAnswer(invocation -> "https://accounts.google.com/authorize?state=" + invocation.getArgument(0));
        when(googleOAuthClient.exchangeCode("valid-code")).thenReturn("access-token");
        when(googleOAuthClient.fetchProfile("access-token"))
                .thenReturn(new OAuthProviderClient.ProviderProfile("google-connect-1", "conecta1@example.com"));

        String authorizeUrlResponse = mockMvc.perform(post("/api/v1/auth/oauth/google/authorize-url")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(authorizeUrlResponse).get("authorizationUrl").asText();

        mockMvc.perform(get("/api/v1/auth/oauth/google/callback")
                        .param("code", "valid-code")
                        .param("state", extractState(url)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/conta/seguranca?connected=google"))
                .andExpect(cookie().doesNotExist("refresh_token"));

        var userId = users.findByEmailNormalized("conecta1@example.com").orElseThrow().id();
        assertThat(links.findByUserId(userId)).hasSize(1);
    }

    @Test
    void connectingAProviderIdentityAlreadyLinkedElsewhereFailsAndRedirectsToSecurity() throws Exception {
        String firstAccountToken = registerAndLogin("dono-original@example.com");
        String secondAccountToken = registerAndLogin("conecta2@example.com");
        when(googleOAuthClient.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(googleOAuthClient.authorizationUrl(any()))
                .thenAnswer(invocation -> "https://accounts.google.com/authorize?state=" + invocation.getArgument(0));
        when(googleOAuthClient.exchangeCode("code-original")).thenReturn("token-original");
        when(googleOAuthClient.fetchProfile("token-original"))
                .thenReturn(new OAuthProviderClient.ProviderProfile("google-shared", "dono-original@example.com"));
        when(googleOAuthClient.exchangeCode("code-second")).thenReturn("token-second");
        when(googleOAuthClient.fetchProfile("token-second"))
                .thenReturn(new OAuthProviderClient.ProviderProfile("google-shared", "conecta2@example.com"));

        String firstAuthorizeUrl = mockMvc.perform(post("/api/v1/auth/oauth/google/authorize-url")
                        .header("Authorization", "Bearer " + firstAccountToken))
                .andReturn().getResponse().getContentAsString();
        String firstState = extractState(objectMapper.readTree(firstAuthorizeUrl).get("authorizationUrl").asText());
        mockMvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "code-original").param("state", firstState))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/conta/seguranca?connected=google"));

        String secondAuthorizeUrl = mockMvc.perform(post("/api/v1/auth/oauth/google/authorize-url")
                        .header("Authorization", "Bearer " + secondAccountToken))
                .andReturn().getResponse().getContentAsString();
        String secondState = extractState(objectMapper.readTree(secondAuthorizeUrl).get("authorizationUrl").asText());

        mockMvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "code-second").param("state", secondState))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://app.example.com/conta/seguranca?connectError=oauth_failed"));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"frase segura de teste\"}"))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"frase segura de teste\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private String extractState(String url) {
        Matcher matcher = Pattern.compile("state=([^&]+)").matcher(url);
        if (!matcher.find()) {
            throw new IllegalStateException("state ausente na URL: " + url);
        }
        return matcher.group(1);
    }
}
