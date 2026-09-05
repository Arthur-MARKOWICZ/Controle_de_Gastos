package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationApiIntegrationTest {

    private static final String EMAIL = "pessoa.teste@example.com";
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
    JwtDecoder jwtDecoder;

    @Autowired
    AuthAttemptRepository authAttempts;

    @Autowired
    AuthenticationService authenticationService;

    @Autowired
    UserAccountRepository users;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void registrationDoesNotRevealWhetherEmailAlreadyExists() throws Exception {
        String payload = credentials(EMAIL.toUpperCase(), PASSWORD);

        MvcResult first = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void loginExposesCurrentUserAndLogoutRevokesAccessImmediately() throws Exception {
        register("sessao@example.com", PASSWORD);

        MvcResult login = login("sessao@example.com", PASSWORD);
        String accessToken = tokenFrom(login);
        Cookie refreshCookie = login.getResponse().getCookie("refresh_token");

        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("sessao@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotationRejectsReuseAndRevokesTheSessionFamily() throws Exception {
        register("rotacao@example.com", PASSWORD);
        MvcResult login = login("rotacao@example.com", PASSWORD);
        Cookie originalRefresh = login.getResponse().getCookie("refresh_token");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(originalRefresh))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedRefresh = refreshed.getResponse().getCookie("refresh_token");

        assertThat(rotatedRefresh).isNotNull();
        assertThat(rotatedRefresh.getValue()).isNotEqualTo(originalRefresh.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(originalRefresh))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotatedRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsUseTheSameExternalResponse() throws Exception {
        register("existente@example.com", PASSWORD);
        register("bloqueada@example.com", PASSWORD);
        var blocked = users.findByEmailNormalized("bloqueada@example.com").orElseThrow();
        blocked.block(Instant.now());
        users.save(blocked);

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("existente@example.com", "senha totalmente errada")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownAccount = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ausente@example.com", "senha totalmente errada")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult blockedAccount = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("bloqueada@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(unknownAccount.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
        assertThat(blockedAccount.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void malformedLoginDataUsesTheSameGenericCredentialResponse() throws Exception {
        MvcResult malformedEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("email-invalido", "senha totalmente errada")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult shortPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ausente@example.com", "curta")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(shortPassword.getResponse().getContentAsString())
                .isEqualTo(malformedEmail.getResponse().getContentAsString());
    }

    @Test
    void accessTokenContainsOnlyTheApprovedClaimsAndExpiresInFifteenMinutes() throws Exception {
        register("claims@example.com", PASSWORD);

        Jwt jwt = jwtDecoder.decode(tokenFrom(login("claims@example.com", PASSWORD)));

        assertThat(jwt.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "aud", "sub", "sid", "jti", "iat", "nbf", "exp");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getHeaders()).doesNotContainKey("kid");
        assertThat(jwt.getClaimAsString("email")).isNull();
    }

    @Test
    void repeatedInvalidLoginsAreRateLimitedWithoutChangingTheCredentialError() throws Exception {
        String payload = credentials("limitado@example.com", "senha totalmente errada");
        String firstBody = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            if (firstBody == null) {
                firstBody = result.getResponse().getContentAsString();
            }
            assertThat(result.getResponse().getContentAsString()).isEqualTo(firstBody);
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void invalidRefreshDeletesTheCookieAndExternalOriginsCannotUseIt() throws Exception {
        Cookie invalid = new Cookie("refresh_token", "not-a-refresh-token");
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(invalid))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "https://external.example")
                        .cookie(invalid))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicRegistrationIsLimitedWithoutChangingItsAcceptedResponse() throws Exception {
        String acceptedBody = null;
        for (int attempt = 1; attempt <= 6; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials("cadastro-" + attempt + "@example.com", PASSWORD)))
                    .andExpect(status().isAccepted())
                    .andReturn();
            if (acceptedBody == null) {
                acceptedBody = result.getResponse().getContentAsString();
            }
            assertThat(result.getResponse().getContentAsString()).isEqualTo(acceptedBody);
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("cadastro-6@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRegistrationsCreateExactlyOneAccountWithoutLeakingTheCollision() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Runnable registration = () -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            authenticationService.register("concorrente@example.com", PASSWORD, "concurrency-test");
        };
        CompletableFuture<Void> first = CompletableFuture.runAsync(registration);
        CompletableFuture<Void> second = CompletableFuture.runAsync(registration);

        ready.await();
        start.countDown();
        CompletableFuture.allOf(first, second).join();

        assertThat(users.findByEmailNormalized("concorrente@example.com")).isPresent();
    }

    @Test
    void protectedEndpointsUseTheStableAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Não foi possível autenticar com os dados informados."));
    }

    @Test
    void registrationCreatesADisabledMfaCredential() throws Exception {
        register("mfa-disabled@example.com", PASSWORD);
        var user = users.findByEmailNormalized("mfa-disabled@example.com").orElseThrow();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM totp_credential WHERE user_id = ?",
                String.class,
                user.id()
        );

        assertThat(status).isEqualTo("DISABLED");
    }

    @Test
    void persistedCredentialIsStoredWithTheUserAndNeverContainsThePassword() throws Exception {
        register("hash@example.com", PASSWORD);
        var user = users.findByEmailNormalized("hash@example.com").orElseThrow();

        String stored = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE id = ?",
                String.class,
                user.id()
        );

        assertThat(stored).startsWith("$2");
        assertThat(stored).doesNotContain(PASSWORD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.password_credential')",
                String.class
        )).isNull();
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isAccepted());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.get("accessToken").asText();
    }

    private String credentials(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new Credentials(email, password));
    }

    private record Credentials(String email, String password) {
    }
}
