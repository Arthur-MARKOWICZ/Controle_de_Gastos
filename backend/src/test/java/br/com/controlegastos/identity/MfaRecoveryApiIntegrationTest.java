package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

@SpringBootTest(properties = "app.auth.mfa.recovery-session-lifetime=2s")
@AutoConfigureMockMvc
@Testcontainers
class MfaRecoveryApiIntegrationTest {

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
    void recoveryCodeIssuesARestrictedTokenThatOnlyReachesTheMfaSetupEndpoints() throws Exception {
        String email = "mfa-recovery@example.com";
        String accessToken = registerAndLogin(email);
        UUID userId = users.findByEmailNormalized(email).orElseThrow().id();
        String firstManualEntryKey = enroll(accessToken);
        List<String> recoveryCodes = confirm(accessToken, codeFor(firstManualEntryKey));

        String challengeId = loginChallenge(email);
        String recoveryCode = recoveryCodes.get(0);

        MvcResult recoveryResult = mockMvc.perform(post("/api/v1/auth/mfa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryBody(challengeId, recoveryCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restrictedToken").isNotEmpty())
                .andReturn();
        assertThat(recoveryResult.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(activeSessionCount(userId)).isZero();
        String restrictedToken = objectMapper.readTree(recoveryResult.getResponse().getContentAsByteArray())
                .get("restrictedToken").asText();

        String secondChallengeId = loginChallenge(email);
        mockMvc.perform(post("/api/v1/auth/mfa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryBody(secondChallengeId, recoveryCode)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + restrictedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESTRICTED_SESSION"));
        mockMvc.perform(get("/api/v1/ledger/summary").header("Authorization", "Bearer " + restrictedToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/mfa/disable").header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/income").header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        MvcResult newEnrollResult = mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String newManualEntryKey = objectMapper.readTree(newEnrollResult.getResponse().getContentAsByteArray())
                .get("manualEntryKey").asText();
        MvcResult confirmResult = mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeBody(codeFor(newManualEntryKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn();
        assertThat(confirmResult.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        Long endedOldCodes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recovery_code WHERE user_id = ? "
                        + "AND (consumed_at IS NOT NULL OR invalidated_at IS NOT NULL)",
                Long.class, userId);
        assertThat(endedOldCodes).isEqualTo(10L);
        Long activeCodes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recovery_code WHERE user_id = ? "
                        + "AND consumed_at IS NULL AND invalidated_at IS NULL",
                Long.class, userId);
        assertThat(activeCodes).isEqualTo(10L);
    }

    @Test
    void anExpiredRestrictedTokenIsRejected() throws Exception {
        String email = "mfa-recovery-expired@example.com";
        String accessToken = registerAndLogin(email);
        String manualEntryKey = enroll(accessToken);
        List<String> recoveryCodes = confirm(accessToken, codeFor(manualEntryKey));

        String challengeId = loginChallenge(email);
        MvcResult recoveryResult = mockMvc.perform(post("/api/v1/auth/mfa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryBody(challengeId, recoveryCodes.get(0))))
                .andExpect(status().isOk())
                .andReturn();
        String restrictedToken = objectMapper.readTree(recoveryResult.getResponse().getContentAsByteArray())
                .get("restrictedToken").asText();

        Thread.sleep(2_100);

        mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private long activeSessionCount(UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_session WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isAccepted());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private String loginChallenge(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("challengeId").asText();
    }

    private String enroll(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("manualEntryKey").asText();
    }

    private List<String> confirm(String accessToken, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeBody(code)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        List<String> codes = new java.util.ArrayList<>();
        body.get("recoveryCodes").forEach(node -> codes.add(node.asText()));
        return codes;
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

    private String recoveryBody(String challengeId, String recoveryCode) throws Exception {
        return objectMapper.writeValueAsString(new MfaRecovery(challengeId, recoveryCode));
    }

    private record Credentials(String email, String password) { }
    private record PasswordConfirmation(String password) { }
    private record CodeConfirmation(String code) { }
    private record MfaRecovery(String challengeId, String recoveryCode) { }
}
