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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(properties = "app.auth.mfa.pending-setup-lifetime=3s")
@AutoConfigureMockMvc
@Testcontainers
class MfaEnrollmentApiIntegrationTest {

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
    void startingEnrollmentWithTheWrongPasswordFails() throws Exception {
        String accessToken = registerAndLogin("mfa-wrong-password@example.com");

        mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody("senha totalmente errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startingEnrollmentTwiceInvalidatesThePreviousPendingSecret() throws Exception {
        String email = "mfa-restart@example.com";
        String accessToken = registerAndLogin(email);
        UUID userId = users.findByEmailNormalized(email).orElseThrow().id();

        String firstKey = enroll(accessToken);
        byte[] firstCiphertext = ciphertextFor(userId);
        String secondKey = enroll(accessToken);
        byte[] secondCiphertext = ciphertextFor(userId);

        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(secondCiphertext).isNotEqualTo(firstCiphertext);

        mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeBody(codeFor(firstKey))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmingEnablesMfaRevokesSessionsAndNeverStoresTheSecretInTheClear() throws Exception {
        String email = "mfa-confirm@example.com";
        String accessToken = registerAndLogin(email);
        UUID userId = users.findByEmailNormalized(email).orElseThrow().id();

        String manualEntryKey = enroll(accessToken);
        String code = codeFor(manualEntryKey);

        mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeBody(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10));

        assertThat(statusOf(userId)).isEqualTo("ENABLED");
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        byte[] ciphertext = ciphertextFor(userId);
        assertThat(new String(ciphertext, StandardCharsets.UTF_8)).doesNotContain(manualEntryKey);
    }

    @Test
    void confirmingAfterExpirationFailsAndLeavesTheCredentialPending() throws Exception {
        String email = "mfa-expired@example.com";
        String accessToken = registerAndLogin(email);
        UUID userId = users.findByEmailNormalized(email).orElseThrow().id();

        String manualEntryKey = enroll(accessToken);
        Thread.sleep(3_100);
        String code = codeFor(manualEntryKey);

        mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeBody(code)))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf(userId)).isEqualTo("PENDING");
    }

    @Test
    void disablingWithTheCorrectPasswordRevokesSessions() throws Exception {
        String email = "mfa-disable@example.com";
        String accessToken = registerAndLogin(email);
        UUID userId = users.findByEmailNormalized(email).orElseThrow().id();
        String manualEntryKey = enroll(accessToken);
        confirmWithFreshCode(accessToken, manualEntryKey);

        String secondAccessToken = loginWithMfa(email, manualEntryKey);

        mockMvc.perform(post("/api/v1/mfa/disable")
                        .header("Authorization", "Bearer " + secondAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(statusOf(userId)).isEqualTo("DISABLED");
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isUnauthorized());
    }

    private void confirmWithFreshCode(String accessToken, String manualEntryKey) throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeBody(codeFor(manualEntryKey))))
                .andExpect(status().isOk());
    }

    private String statusOf(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM totp_credential WHERE user_id = ?", String.class, userId);
    }

    private byte[] ciphertextFor(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT secret_ciphertext FROM totp_credential WHERE user_id = ?", byte[].class, userId);
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isAccepted());
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.get("accessToken").asText();
    }

    private String loginWithMfa(String email, String manualEntryKey) throws Exception {
        MvcResult challengeResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn();
        String challengeId = objectMapper.readTree(challengeResult.getResponse().getContentAsByteArray())
                .get("challengeId").asText();
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerify(challengeId, codeFor(manualEntryKey)))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(verifyResult.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private String enroll(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("manualEntryKey").asText();
    }

    private String codeFor(String base32Secret) throws Exception {
        long counter = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        return CODE_GENERATOR.generate(base32Secret, counter);
    }

    private String credentials(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new Credentials(email, password));
    }

    private String passwordBody(String password) throws Exception {
        return objectMapper.writeValueAsString(new PasswordConfirmation(password));
    }

    private String codeBody(String code) throws Exception {
        return objectMapper.writeValueAsString(new CodeConfirmation(code));
    }

    private record Credentials(String email, String password) { }
    private record PasswordConfirmation(String password) { }
    private record CodeConfirmation(String code) { }
    private record MfaVerify(String challengeId, String code) { }
}
