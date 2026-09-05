package br.com.controlegastos.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.application.SessionService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LoginMethodsApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.cookie-name", () -> "refresh_token");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SessionService sessions;

    @Autowired
    JwtDecoder jwtDecoder;

    @BeforeEach
    void resetRateLimits() {
        jdbcTemplate.update("DELETE FROM auth_attempt");
    }

    @Test
    void loginMethodsStatusReportsThePasswordCredentialForARegularAccount() throws Exception {
        String accessToken = registerAndLogin("metodos1@example.com");

        mockMvc.perform(get("/api/v1/auth/login-methods").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.linkedProviders").isEmpty());
    }

    @Test
    void addingAPasswordFailsWhenTheAccountAlreadyHasOne() throws Exception {
        String accessToken = registerAndLogin("metodos2@example.com");

        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordBody("outra frase segura"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_ALREADY_SET"));
    }

    @Test
    void addsAPasswordToAProviderOnlyAccount() throws Exception {
        String accessToken = createProviderOnlyAccountAndLogin("metodos3@example.com", "GOOGLE", "google-1");

        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordBody("uma frase bem segura"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/login-methods").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    void unlinkingTheOnlyLoginMethodIsRejected() throws Exception {
        String accessToken = createProviderOnlyAccountAndLogin("metodos4@example.com", "GOOGLE", "google-2");

        mockMvc.perform(delete("/api/v1/auth/oauth/google").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD"));
    }

    @Test
    void unlinkingIsAllowedWhenAnotherLoginMethodRemains() throws Exception {
        String accessToken = registerAndLogin("metodos5@example.com");
        linkProvider(currentUserId(accessToken), "GITHUB", "github-5");

        mockMvc.perform(delete("/api/v1/auth/oauth/github").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/login-methods").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedProviders").isEmpty());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return tokenFrom(login);
    }

    // Cria uma conta só com provedor social diretamente no banco e emite a sessão pelo mesmo
    // SessionService usado pelo login normal, evitando duplicar todo o fluxo OAuth (código,
    // troca de token, perfil) já coberto em OAuthApiIntegrationTest.
    private String createProviderOnlyAccountAndLogin(String email, String provider, String providerUserId) {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO user_account (id, email_normalized, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'ACTIVE', ?, ?)",
                userId, email, now, now);
        jdbcTemplate.update(
                "INSERT INTO totp_credential (user_id, status, created_at, updated_at) VALUES (?, 'DISABLED', ?, ?)",
                userId, now, now);
        linkProvider(userId, provider, providerUserId);
        return sessions.start(userId).accessToken();
    }

    private void linkProvider(UUID userId, String provider, String providerUserId) {
        jdbcTemplate.update(
                "INSERT INTO identity_provider_link (id, user_id, provider, provider_user_id, provider_email, linked_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, provider, providerUserId, "irrelevante@example.com",
                Timestamp.from(Instant.now()));
    }

    private UUID currentUserId(String accessToken) {
        return UUID.fromString(jwtDecoder.decode(accessToken).getSubject());
    }

    private String tokenFrom(MvcResult result) {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.get("accessToken").asText();
    }

    private String credentials(String email, String password) {
        return objectMapper.writeValueAsString(new Credentials(email, password));
    }

    private record Credentials(String email, String password) {
    }

    private record PasswordBody(String password) {
    }
}
