package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MfaLoginApiIntegrationTest {

    private static final String EMAIL = "mfa-login@example.com";
    private static final String PASSWORD = "frase segura de teste";
    private static final CodeGenerator CODE_GENERATOR = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);

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
    @Autowired UserAccountRepository users;
    @Autowired AuthAttemptRepository attempts;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        attempts.deleteAll();
    }

    @Test
    void loginWithMfaEnabledIssuesAChallengeAndOnlyTheCorrectCodeCreatesExactlyOneSession() throws Exception {
        register(EMAIL, PASSWORD);
        String accessToken = loginToken(EMAIL, PASSWORD);
        String manualEntryKey = enroll(accessToken);
        String firstCode = codeFor(manualEntryKey);
        confirmEnrollment(accessToken, firstCode);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        MvcResult challengeResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andReturn();
        assertThat(challengeResult.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        var user = users.findByEmailNormalized(EMAIL).orElseThrow();
        assertThat(activeSessionCount(user.id())).isZero();

        String challengeId = objectMapper.readTree(challengeResult.getResponse().getContentAsByteArray())
                .get("challengeId").asText();

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(challengeId, "000000")))
                .andExpect(status().isUnauthorized());
        assertThat(activeSessionCount(user.id())).isZero();

        String secondCode = codeFor(manualEntryKey);
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(challengeId, secondCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertThat(activeSessionCount(user.id())).isEqualTo(1);
    }

    @Test
    void repeatedInvalidMfaVerificationsAreRateLimited() throws Exception {
        String payload = verifyBody("desafio-inexistente", "000000");
        String firstBody = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            if (firstBody == null) {
                firstBody = result.getResponse().getContentAsString();
            }
            assertThat(result.getResponse().getContentAsString()).isEqualTo(firstBody);
        }

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests());
    }

    private long activeSessionCount(java.util.UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_session WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isAccepted());
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private String enroll(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordConfirmation(PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("manualEntryKey").asText();
    }

    private void confirmEnrollment(String accessToken, String code) throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CodeConfirmation(code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));
    }

    private String codeFor(String base32Secret) throws Exception {
        long counter = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        return CODE_GENERATOR.generate(base32Secret, counter);
    }

    private String credentials(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new Credentials(email, password));
    }

    private String verifyBody(String challengeId, String code) throws Exception {
        return objectMapper.writeValueAsString(new MfaVerify(challengeId, code));
    }

    private record Credentials(String email, String password) { }
    private record PasswordConfirmation(String password) { }
    private record CodeConfirmation(String code) { }
    private record MfaVerify(String challengeId, String code) { }
}
