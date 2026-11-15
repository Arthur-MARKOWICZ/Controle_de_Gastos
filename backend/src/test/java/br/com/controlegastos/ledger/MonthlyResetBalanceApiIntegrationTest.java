package br.com.controlegastos.ledger;

import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class MonthlyResetBalanceApiIntegrationTest {

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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void limitDoesNotCarryUnusedBalanceIntoTheNextMonth() throws Exception {
        String token = tokenFor("limite-sem-carry@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "100.00");

        summary(token, "2026-09")
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("100.00"));

        register(token, envelopeId, "EXPENSE", "40.00", "2026-09-15");

        summary(token, "2026-09")
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("60.00"));

        summary(token, "2026-10")
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("100.00"));
    }

    @Test
    void fixedDoesNotCarryUnusedOrSpentBalanceIntoTheNextMonth() throws Exception {
        String token = tokenFor("compromisso-sem-carry@example.com");
        String envelopeId = createEnvelope(token, "Aluguel", "FIXED", "1500.00");

        register(token, envelopeId, "EXPENSE", "500.00", "2026-09-05");

        summary(token, "2026-09")
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1000.00"));

        summary(token, "2026-10")
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1500.00"));
    }

    @Test
    void limitCountsOnlyMovementsOfTheConsultedMonth() throws Exception {
        String token = tokenFor("limite-mes-consultado@example.com");
        String envelopeId = createEnvelope(token, "Lazer", "LIMIT", "200.00");

        register(token, envelopeId, "EXPENSE", "50.00", "2026-09-10");
        // Outubro é futuro para o relógio fixo (15/09): grava como se a API os tivesse aceitado em outubro.
        insertEntry(envelopeId, "CONTRIBUTION", "20.00", "2026-10-02");
        insertEntry(envelopeId, "EXPENSE", "30.00", "2026-10-03");

        summary(token, "2026-10")
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("190.00"));
    }

    private org.springframework.test.web.servlet.ResultActions summary(String token, String month) throws Exception {
        return mockMvc.perform(get("/api/v1/ledger/summary?month=" + month).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String createEnvelope(String token, String name, String purpose, String baseAmount) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name, "purpose", purpose, "baseAmount", money(baseAmount)));
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String register(String token, String envelopeId, String kind, String amount, String occurredAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", kind, "amount", money(amount), "occurredAt", occurredAt))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(new Credentials(email, PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return response.get("accessToken").asString();
    }

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private record Credentials(String email, String password) {}

    private org.springframework.test.web.servlet.ResultActions postEntry(String token, String envelopeId,
                                                                          Object body) throws Exception {
        var request = post("/api/v1/envelopes/{id}/entries", envelopeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("gasto zero é recusado com 400 INVALID_REQUEST")
    void gastoZeroRecusado() throws Exception {
        String token = tokenFor("reset-zero@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "100.00");

        postEntry(token, envelopeId, Map.of("kind", "EXPENSE", "amount", money("0.00"), "occurredAt", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("gasto com três casas decimais é recusado com 400")
    void gastoComTresCasasRecusado() throws Exception {
        String token = tokenFor("reset-scale@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "100.00");

        postEntry(token, envelopeId, Map.of("kind", "EXPENSE", "amount", money("10.555"), "occurredAt", "2026-09-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("gasto com data dois dias à frente do relógio é recusado com 400")
    void gastoNoFuturoRecusado() throws Exception {
        String token = tokenFor("reset-future@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "100.00");

        postEntry(token, envelopeId, Map.of("kind", "EXPENSE", "amount", money("1.00"), "occurredAt", "2026-09-17"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("terceiro não lança gasto na verba de outro titular")
    void terceiroNaoLanca() throws Exception {
        String owner = tokenFor("reset-owner@example.com");
        String stranger = tokenFor("reset-stranger@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "LIMIT", "100.00");

        int status = postEntry(stranger, envelopeId, Map.of("kind", "EXPENSE", "amount", money("1.00"), "occurredAt", "2026-09-10"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isIn(403, 404);
        summary(owner, "2026-09").andExpect(jsonPath("$.envelopes[0].available.amount").value("100.00"));
    }

    @Test
    @DisplayName("lançar sem sessão responde 401")
    void lancarSemSessao() throws Exception {
        postEntry(null, java.util.UUID.randomUUID().toString(),
                Map.of("kind", "EXPENSE", "amount", money("1.00"), "occurredAt", "2026-09-10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("resumo com mês malformado responde 400")
    void resumoComMesMalformado() throws Exception {
        String token = tokenFor("reset-bad-month@example.com");

        mockMvc.perform(get("/api/v1/ledger/summary?month=2026-13").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private void insertEntry(String envelopeId, String kind, String amount, String occurredAt) {
        java.util.UUID envelope = java.util.UUID.fromString(envelopeId);
        java.util.UUID owner = jdbc.queryForObject("SELECT owner_id FROM envelope WHERE id = ?", java.util.UUID.class, envelope);
        jdbc.update("""
                INSERT INTO ledger_entry(id, envelope_id, owner_id, author_id, amount, kind, occurred_at, created_at)
                VALUES (?, ?, ?, ?, ?::numeric, ?, ?::date, now())
                """, java.util.UUID.randomUUID(), envelope, owner, owner, amount, kind, occurredAt);
    }
}
