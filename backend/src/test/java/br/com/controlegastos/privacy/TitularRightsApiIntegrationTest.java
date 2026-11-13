package br.com.controlegastos.privacy;

import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(FixedClockTestConfiguration.class)
class TitularRightsApiIntegrationTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository authAttempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void exportsAndErasesTitularDataWhilePreservingAuthorshipOnSharedEntries() throws Exception {
        String ownerToken = tokenFor("titular-owner@example.com");
        UUID ownerId = userId("titular-owner@example.com");
        String participantToken = tokenFor("titular-participant@example.com");
        UUID participantId = userId("titular-participant@example.com");

        putIncome(ownerToken, "1000.00");
        String envelopeId = createEnvelope(ownerToken, "Mercado", "LIMIT", "200.00");
        jdbc.update(
                "INSERT INTO envelope_participant(envelope_id, user_id, added_at, added_by) VALUES (?, ?, now(), ?)",
                UUID.fromString(envelopeId), participantId, ownerId);
        String sharedExpenseId = createExpense(participantToken, envelopeId, "25.00");

        mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + participantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.email").value("titular-participant@example.com"))
                .andExpect(jsonPath("$.ledgerEntries.length()").value(1));

        mockMvc.perform(delete("/api/v1/privacy/account")
                        .header("Authorization", "Bearer " + participantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "titular-participant@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        Integer remainingEntries = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE id = ?", Integer.class, UUID.fromString(sharedExpenseId));
        assertThat(remainingEntries).isEqualTo(1);

        String status = jdbc.queryForObject(
                "SELECT status FROM user_account WHERE id = ?", String.class, participantId);
        assertThat(status).isEqualTo("DELETED");
    }

    private void putIncome(String token, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", amount))))
                .andExpect(status().isOk());
    }

    private String createEnvelope(String token, String name, String purpose, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", purpose, "baseAmount", money(baseAmount)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String createExpense(String token, String envelopeId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "EXPENSE",
                                "amount", money(amount),
                                "occurredAt", "2026-09-10"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return response.get("accessToken").asString();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
    }

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private org.springframework.test.web.servlet.ResultActions erase(String token, Object body) throws Exception {
        var request = delete("/api/v1/privacy/account").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("senha errada na eliminação responde 401 e preserva verbas e renda")
    void senhaErradaPreservaDados() throws Exception {
        String token = tokenFor("titular-senha-errada@example.com");
        putIncome(token, "1000.00");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "200.00");

        erase(token, Map.of("password", "senha errada de teste")).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envelope WHERE id = ?", Integer.class, UUID.fromString(envelopeId)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monthly_income WHERE owner_id = ?", Integer.class,
                userId("titular-senha-errada@example.com"))).isEqualTo(1);
    }

    @Test
    @DisplayName("eliminação sem senha responde 4xx e não apaga nada")
    void eliminacaoSemSenha() throws Exception {
        String token = tokenFor("titular-sem-senha@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "200.00");

        int status = erase(token, Map.of()).andReturn().getResponse().getStatus();

        assertThat(status).isIn(400, 401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envelope WHERE id = ?", Integer.class, UUID.fromString(envelopeId)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("exportação não traz verbas de outro titular")
    void exportacaoIsolada() throws Exception {
        String me = tokenFor("titular-export-me@example.com");
        String other = tokenFor("titular-export-other@example.com");
        createEnvelope(other, "Segredo", "LIMIT", "100.00");

        mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes.length()").value(0))
                .andExpect(jsonPath("$.identity.email").value("titular-export-me@example.com"));
    }

    @Test
    @DisplayName("exportação traz valores monetários como string com duas casas")
    void exportacaoComValoresDecimais() throws Exception {
        String token = tokenFor("titular-export-money@example.com");
        putIncome(token, "1000.00");

        mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.income[0].amount").value("1000.00"))
                .andExpect(jsonPath("$.identity.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("token de conta já eliminada não exporta mais nada")
    void contaEliminadaNaoExporta() throws Exception {
        String token = tokenFor("titular-eliminado@example.com");
        erase(token, Map.of("password", PASSWORD)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("exportar e eliminar sem sessão respondem 401")
    void semSessao() throws Exception {
        mockMvc.perform(get("/api/v1/privacy/export")).andExpect(status().isUnauthorized());
        erase(null, Map.of("password", PASSWORD)).andExpect(status().isUnauthorized());
    }
}
