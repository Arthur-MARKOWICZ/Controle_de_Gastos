package br.com.controlegastos.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.application.PasswordResetMailSender;
import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.auth.password-reset.public-url=https://gastos.example.com")
@AutoConfigureMockMvc
@Testcontainers
@Import(PasswordResetApiIntegrationTest.MailConfiguration.class)
class PasswordResetApiIntegrationTest {

    private static final String EMAIL = "recuperacao@example.com";
    private static final String SECOND_EMAIL = "recuperacao-token-invalido@example.com";
    private static final String OLD_PASSWORD = "frase segura de teste";
    private static final String NEW_PASSWORD = "nova frase segura de teste";
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
    @Autowired CapturingMailSender mail;

    @BeforeEach
    void resetState() {
        attempts.deleteAll();
        mail.clear();
    }

    @Test
    void requestsAreGenericAndAValidTokenChangesThePasswordAndRevokesEverySession() throws Exception {
        register(EMAIL, OLD_PASSWORD);
        String firstSession = loginToken(EMAIL, OLD_PASSWORD);
        String secondSession = loginToken(EMAIL, OLD_PASSWORD);

        MvcResult existing = requestReset(EMAIL);
        MvcResult unknown = requestReset("ausente@example.com");

        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(existing.getResponse().getContentAsString());
        assertThat(mail.resetUrl()).startsWith("https://gastos.example.com/redefinir-senha#token=");
        assertThat(jdbcTemplate.queryForObject(
                "select token_hash from password_reset_token where user_id = ?", String.class,
                users.findByEmailNormalized(EMAIL).orElseThrow().id()))
                .doesNotContain("token=")
                .doesNotContain(mail.rawToken());

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ResetRequest(mail.rawToken(), NEW_PASSWORD))))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + firstSession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + secondSession))
                .andExpect(status().isUnauthorized());
        assertThat(loginStatus(EMAIL, OLD_PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(EMAIL, NEW_PASSWORD)).isEqualTo(200);
        assertThat(users.findByEmailNormalized(EMAIL).orElseThrow().emailVerifiedAt()).isNotNull();
        assertThat(mail.passwordChangedRecipient()).isEqualTo(EMAIL);
    }

    @Test
    void consumedOrUnknownTokensDoNotChangeThePassword() throws Exception {
        register(SECOND_EMAIL, OLD_PASSWORD);
        requestReset(SECOND_EMAIL);

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ResetRequest("not-a-valid-token", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());

        assertThat(loginStatus(SECOND_EMAIL, OLD_PASSWORD)).isEqualTo(200);
    }

    @Test
    void resettingThePasswordOfAnMfaEnabledAccountPreservesMfa() throws Exception {
        String email = "recuperacao-com-mfa@example.com";
        register(email, OLD_PASSWORD);
        String accessToken = loginToken(email, OLD_PASSWORD);

        MvcResult enrollResult = mockMvc.perform(post("/api/v1/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordConfirmation(OLD_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String manualEntryKey = objectMapper.readTree(enrollResult.getResponse().getContentAsByteArray())
                .get("manualEntryKey").asText();
        long counter = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        String code = CODE_GENERATOR.generate(manualEntryKey, counter);
        mockMvc.perform(post("/api/v1/mfa/enroll/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CodeConfirmation(code))))
                .andExpect(status().isOk());

        requestReset(email);
        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ResetRequest(mail.rawToken(), NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM totp_credential WHERE user_id = ?", String.class,
                users.findByEmailNormalized(email).orElseThrow().id());
        assertThat(status).isEqualTo("ENABLED");
    }

    @Test
    void mailDeliveryFailureDoesNotRevealWhetherTheAccountExists() throws Exception {
        register("recuperacao-falha@example.com", OLD_PASSWORD);
        mail.failNextReset();

        requestReset("recuperacao-falha@example.com");
    }

    private MvcResult requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new EmailRequest(email))))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credentials(email, password))))
                .andExpect(status().isAccepted());
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credentials(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private int loginStatus(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credentials(email, password))))
                .andReturn().getResponse().getStatus();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private record EmailRequest(String email) { }
    private record ResetRequest(String token, String newPassword) { }
    private record Credentials(String email, String password) { }
    private record PasswordConfirmation(String password) { }
    private record CodeConfirmation(String code) { }

    @TestConfiguration
    static class MailConfiguration {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    static class CapturingMailSender implements PasswordResetMailSender {
        private final AtomicReference<String> resetUrl = new AtomicReference<>();
        private final AtomicReference<String> changedRecipient = new AtomicReference<>();
        private boolean failNextReset;

        @Override
        public void sendPasswordReset(String recipient, String url) {
            if (failNextReset) {
                failNextReset = false;
                throw new IllegalStateException("SMTP indisponível");
            }
            resetUrl.set(url);
        }

        @Override
        public void sendPasswordChanged(String recipient) {
            changedRecipient.set(recipient);
        }

        String resetUrl() { return resetUrl.get(); }
        String rawToken() { return resetUrl.get().substring(resetUrl.get().indexOf("token=") + 6); }
        String passwordChangedRecipient() { return changedRecipient.get(); }
        void failNextReset() { failNextReset = true; }
        void clear() { resetUrl.set(null); changedRecipient.set(null); failNextReset = false; }
    }
}
