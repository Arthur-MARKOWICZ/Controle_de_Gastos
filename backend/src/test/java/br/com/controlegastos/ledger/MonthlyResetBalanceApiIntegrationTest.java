package br.com.controlegastos.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
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
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void limitDoesNotCarryUnusedBalanceIntoTheNextMonth() throws Exception {
        String token = tokenFor("limite-sem-carry@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "100.00");
        backdateCreation(envelopeId, "2026-09-01T12:00:00Z");

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
        backdateCreation(envelopeId, "2026-09-01T12:00:00Z");

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
        backdateCreation(envelopeId, "2026-09-01T12:00:00Z");

        register(token, envelopeId, "EXPENSE", "50.00", "2026-09-10");
        register(token, envelopeId, "CONTRIBUTION", "20.00", "2026-10-02");
        register(token, envelopeId, "EXPENSE", "30.00", "2026-10-03");

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

    private void backdateCreation(String envelopeId, String createdAt) {
        jdbc.update("UPDATE envelope SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse(createdAt)), UUID.fromString(envelopeId));
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
}
