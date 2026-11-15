package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Resgate do código de handoff OAuth pelo app (spec identity/oauth-mobile-handoff). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OAuthMobileHandoffApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String HANDOFF = "/api/v1/auth/oauth/mobile-handoff";

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        attempts.deleteAll();
    }

    @Test
    @DisplayName("troca um código válido por uma sessão Bearer do titular")
    void trocaCodigoValidoPorSessao() throws Exception {
        UUID userId = register("handoff-valido@example.com");
        String code = issueHandoff(userId, Instant.now().plus(Duration.ofSeconds(60)));

        MvcResult result = mockMvc.perform(post(HANDOFF).contentType(MediaType.APPLICATION_JSON).content(body(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        String accessToken = json(result).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("handoff-valido@example.com"));
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM oauth_mobile_handoff WHERE code_hash = ?",
                Timestamp.class, sha256(code))).isNotNull();
    }

    @Test
    @DisplayName("recusa código já resgatado com 400 OAUTH_LOGIN_FAILED sem emitir sessão nova")
    void recusaCodigoReutilizado() throws Exception {
        UUID userId = register("handoff-reuso@example.com");
        String code = issueHandoff(userId, Instant.now().plus(Duration.ofSeconds(60)));
        mockMvc.perform(post(HANDOFF).contentType(MediaType.APPLICATION_JSON).content(body(code)))
                .andExpect(status().isOk());
        long sessionsAfterFirst = activeSessions(userId);

        mockMvc.perform(post(HANDOFF).contentType(MediaType.APPLICATION_JSON).content(body(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_LOGIN_FAILED"));

        assertThat(activeSessions(userId)).isEqualTo(sessionsAfterFirst);
    }

    @Test
    @DisplayName("recusa código expirado com 400 OAUTH_LOGIN_FAILED")
    void recusaCodigoExpirado() throws Exception {
        UUID userId = register("handoff-expirado@example.com");
        String code = issueHandoff(userId, Instant.now().minus(Duration.ofSeconds(1)));

        mockMvc.perform(post(HANDOFF).contentType(MediaType.APPLICATION_JSON).content(body(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_LOGIN_FAILED"));

        assertThat(activeSessions(userId)).isZero();
    }

    @Test
    @DisplayName("recusa código desconhecido com a mesma resposta do expirado")
    void recusaCodigoDesconhecidoComMesmaMensagem() throws Exception {
        UUID userId = register("handoff-desconhecido@example.com");
        String expired = issueHandoff(userId, Instant.now().minus(Duration.ofSeconds(1)));

        JsonNode expiredBody = json(mockMvc.perform(post(HANDOFF)
                        .contentType(MediaType.APPLICATION_JSON).content(body(expired)))
                .andExpect(status().isBadRequest()).andReturn());
        JsonNode unknownBody = json(mockMvc.perform(post(HANDOFF)
                        .contentType(MediaType.APPLICATION_JSON).content(body("codigo-que-nunca-existiu")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_LOGIN_FAILED"))
                .andReturn());

        assertThat(unknownBody.get("detail")).isEqualTo(expiredBody.get("detail"));
    }

    @Test
    @DisplayName("resgates concorrentes do mesmo código emitem no máximo uma sessão")
    void resgatesConcorrentesEmitemUmaSessao() throws Exception {
        UUID userId = register("handoff-concorrente@example.com");
        String code = issueHandoff(userId, Instant.now().plus(Duration.ofSeconds(60)));
        Callable<Integer> redeem = () -> mockMvc.perform(post(HANDOFF)
                        .contentType(MediaType.APPLICATION_JSON).content(body(code)))
                .andReturn().getResponse().getStatus();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = pool.invokeAll(List.of(redeem, redeem));
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            pool.shutdownNow();
        }
        assertThat(activeSessions(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("recusa corpo sem código com 400 INVALID_REQUEST")
    void recusaCorpoSemCodigo() throws Exception {
        mockMvc.perform(post(HANDOFF).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("recusa código em branco com 400 INVALID_REQUEST")
    void recusaCodigoEmBranco() throws Exception {
        mockMvc.perform(post(HANDOFF).contentType(MediaType.APPLICATION_JSON).content(body("  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private UUID register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isAccepted());
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
    }

    /** Emite o handoff como o callback OAuth faria: só o hash do código fica no banco. */
    private String issueHandoff(UUID userId, Instant expiresAt) throws Exception {
        String rawCode = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant createdAt = expiresAt.minus(Duration.ofSeconds(60));
        jdbc.update("""
                INSERT INTO oauth_mobile_handoff(id, user_id, code_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, sha256(rawCode), Timestamp.from(createdAt), Timestamp.from(expiresAt));
        return rawCode;
    }

    private long activeSessions(UUID userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM auth_session WHERE user_id = ? AND revoked_at IS NULL", Long.class, userId);
    }

    private String body(String code) throws Exception {
        return objectMapper.writeValueAsString(new Handoff(code));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Credentials(String email, String password) { }
    private record Handoff(String code) { }
}
