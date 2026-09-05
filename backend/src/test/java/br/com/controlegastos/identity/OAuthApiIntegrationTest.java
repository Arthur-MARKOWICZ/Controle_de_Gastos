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
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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

    @MockitoBean
    OAuthProviderClient googleOAuthClient;

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

    private String extractState(String url) {
        Matcher matcher = Pattern.compile("state=([^&]+)").matcher(url);
        if (!matcher.find()) {
            throw new IllegalStateException("state ausente na URL: " + url);
        }
        return matcher.group(1);
    }
}
