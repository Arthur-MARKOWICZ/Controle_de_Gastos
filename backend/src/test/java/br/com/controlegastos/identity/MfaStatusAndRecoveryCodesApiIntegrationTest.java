package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** Contrato de {@code GET /mfa/status} e {@code POST /mfa/recovery-codes} (spec identity/mfa-api). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MfaStatusAndRecoveryCodesApiIntegrationTest {

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
    @Autowired AuthAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        attempts.deleteAll();
    }

    @Test
    @DisplayName("informa DISABLED sem prazo pendente para quem nunca cadastrou MFA")
    void informaDisabledParaQuemNuncaCadastrou() throws Exception {
        String token = registerAndLogin("mfa-status-nunca@example.com");

        mockMvc.perform(get("/api/v1/mfa/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.pendingExpiresAt").doesNotExist());
    }

    @Test
    @DisplayName("informa PENDING com prazo quando o cadastro não foi confirmado")
    void informaPendingComPrazo() throws Exception {
        String token = registerAndLogin("mfa-status-pendente@example.com");
        enroll(token);

        mockMvc.perform(get("/api/v1/mfa/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pendingExpiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("informa ENABLED depois de confirmar o cadastro")
    void informaEnabledDepoisDeConfirmar() throws Exception {
        String email = "mfa-status-ativo@example.com";
        String token = registerAndLogin(email);
        String secret = enroll(token);
        confirm(token, secret);
        String mfaToken = loginWithMfa(email, secret);

        mockMvc.perform(get("/api/v1/mfa/status").header("Authorization", "Bearer " + mfaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"));
    }

    @Test
    @DisplayName("regenera os códigos de recuperação e invalida os anteriores")
    void regeneraCodigosEInvalidaAnteriores() throws Exception {
        String email = "mfa-regenera@example.com";
        String token = registerAndLogin(email);
        String secret = enroll(token);
        List<String> oldCodes = confirm(token, secret);
        String mfaToken = loginWithMfa(email, secret);

        MvcResult result = mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .header("Authorization", "Bearer " + mfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn();
        List<String> newCodes = codesFrom(result);
        assertThat(newCodes).doesNotContainAnyElementsOf(oldCodes);

        mockMvc.perform(post("/api/v1/auth/mfa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryBody(loginChallenge(email), oldCodes.get(0))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    @DisplayName("recusa senha incorreta com 401 PASSWORD_REQUIRED_INVALID e mantém os códigos válidos")
    void recusaSenhaIncorretaEMantemCodigos() throws Exception {
        String email = "mfa-regenera-senha@example.com";
        String token = registerAndLogin(email);
        String secret = enroll(token);
        List<String> codes = confirm(token, secret);
        String mfaToken = loginWithMfa(email, secret);

        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .header("Authorization", "Bearer " + mfaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody("senha errada de teste")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED_INVALID"));

        mockMvc.perform(post("/api/v1/auth/mfa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryBody(loginChallenge(email), codes.get(0))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("recusa senha em branco com 400 INVALID_REQUEST")
    void recusaSenhaEmBranco() throws Exception {
        String token = registerAndLogin("mfa-regenera-vazia@example.com");

        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("recusa regenerar com MFA desabilitado ou pendente com 409 MFA_NOT_ENABLED")
    void recusaRegenerarSemMfaHabilitado() throws Exception {
        String token = registerAndLogin("mfa-regenera-desabilitado@example.com");

        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MFA_NOT_ENABLED"));

        enroll(token);
        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MFA_NOT_ENABLED"));
    }

    @Test
    @DisplayName("recusa conta sem senha (só login social) com 401 PASSWORD_REQUIRED_INVALID")
    void recusaContaSemSenha() throws Exception {
        String email = "mfa-regenera-social@example.com";
        String token = registerAndLogin(email);
        jdbc.update("UPDATE user_account SET password_hash = NULL, password_changed_at = NULL WHERE email_normalized = ?",
                email);

        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED_INVALID"));
    }

    @Test
    @DisplayName("exige sessão para consultar status e regenerar códigos")
    void exigeSessao() throws Exception {
        mockMvc.perform(get("/api/v1/mfa/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isAccepted());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private String enroll(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("manualEntryKey").asText();
    }

    private List<String> confirm(String accessToken, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CodeConfirmation(codeFor(secret)))))
                .andExpect(status().isOk())
                .andReturn();
        return codesFrom(result);
    }

    private String loginChallenge(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andReturn();
        return json(result).get("challengeId").asText();
    }

    private String loginWithMfa(String email, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerify(loginChallenge(email), codeFor(secret)))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private List<String> codesFrom(MvcResult result) throws Exception {
        List<String> codes = new ArrayList<>();
        json(result).get("recoveryCodes").forEach(node -> codes.add(node.asText()));
        return codes;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String codeFor(String base32Secret) throws Exception {
        return CODE_GENERATOR.generate(base32Secret, Math.floorDiv(Instant.now().getEpochSecond(), 30));
    }

    private String passwordBody(String password) throws Exception {
        return objectMapper.writeValueAsString(new PasswordConfirmation(password));
    }

    private String recoveryBody(String challengeId, String recoveryCode) throws Exception {
        return objectMapper.writeValueAsString(new MfaRecovery(challengeId, recoveryCode));
    }

    private record Credentials(String email, String password) { }
    private record PasswordConfirmation(String password) { }
    private record CodeConfirmation(String code) { }
    private record MfaVerify(String challengeId, String code) { }
    private record MfaRecovery(String challengeId, String recoveryCode) { }
}
