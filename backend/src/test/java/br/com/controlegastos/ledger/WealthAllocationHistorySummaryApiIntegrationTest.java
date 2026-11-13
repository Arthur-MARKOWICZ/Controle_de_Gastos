package br.com.controlegastos.ledger;

import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.HashMap;
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
class WealthAllocationHistorySummaryApiIntegrationTest {

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

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void splitsConsumptionAndWealthAllocationInHistorySummary() throws Exception {
        String token = tokenFor("alocacao@example.com");
        setIncome(token, "5000.00");

        String consumptionId = createEnvelope(token, "Mercado", "LIMIT", "400.00", false);
        String investmentId = createEnvelope(token, "Investimentos", "LIMIT", "1000.00", true);

        createExpense(token, consumptionId, "80.00");
        createExpense(token, investmentId, "500.00");

        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-01&to=2026-09-30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.amount").value("80.00"))
                .andExpect(jsonPath("$.applied.amount").value("500.00"))
                .andExpect(jsonPath("$.netBalance.amount").value("4420.00"));
    }

    @Test
    void allowsTogglingWealthAllocationOnExistingEnvelope() throws Exception {
        String token = tokenFor("toggle-alocacao@example.com");
        setIncome(token, "3000.00");

        String envelopeId = createEnvelope(token, "CDB", "LIMIT", "800.00", false);
        createExpense(token, envelopeId, "200.00");

        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-01&to=2026-09-30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.amount").value("200.00"))
                .andExpect(jsonPath("$.applied.amount").value("0.00"));

        MvcResult getResult = mockMvc.perform(get("/api/v1/envelopes/{id}", envelopeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wealthAllocation").value(false))
                .andReturn();
        long version = objectMapper.readTree(getResult.getResponse().getContentAsByteArray()).get("version").asLong();

        mockMvc.perform(patch("/api/v1/envelopes/{id}", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", version,
                                "wealthAllocation", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wealthAllocation").value(true));

        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-01&to=2026-09-30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.amount").value("0.00"))
                .andExpect(jsonPath("$.applied.amount").value("200.00"));
    }

    private String createEnvelope(String token, String name, String purpose, String baseAmount,
                                  boolean wealthAllocation) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("purpose", purpose);
        body.put("baseAmount", money(baseAmount));
        body.put("wealthAllocation", wealthAllocation);
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wealthAllocation").value(wealthAllocation))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private void createExpense(String token, String envelopeId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "EXPENSE",
                                "amount", money(amount),
                                "occurredAt", "2026-09-10"))))
                .andExpect(status().isCreated());
    }

    private void setIncome(String token, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", amount))))
                .andExpect(status().isOk());
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

    @Test
    @DisplayName("resumo do histórico com início depois do fim responde 400")
    void intervaloInvertidoResponde400() throws Exception {
        String token = tokenFor("wealth-range@example.com");

        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-30&to=2026-09-01").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resumo do histórico com data malformada responde 400")
    void dataMalformadaResponde400() throws Exception {
        String token = tokenFor("wealth-bad-date@example.com");

        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-xx&to=2026-09-30").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resumo do histórico sem parâmetros responde 400")
    void semParametrosResponde400() throws Exception {
        String token = tokenFor("wealth-no-params@example.com");

        mockMvc.perform(get("/api/v1/history/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("gastos de outro titular não entram no meu resumo")
    void gastosDeOutroNaoEntram() throws Exception {
        String me = tokenFor("wealth-me@example.com");
        String other = tokenFor("wealth-other@example.com");
        String envelopeId = createEnvelope(other, "Mercado", "LIMIT", "100.00", false);
        createExpense(other, envelopeId, "40.00");

        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-01&to=2026-09-30").header("Authorization", "Bearer " + me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.amount").value("0.00"));
    }

    @Test
    @DisplayName("participante não altera a classificação de patrimônio da verba do proprietário")
    void participanteNaoAlteraClassificacao() throws Exception {
        String owner = tokenFor("wealth-owner@example.com");
        String stranger = tokenFor("wealth-stranger@example.com");
        String envelopeId = createEnvelope(owner, "Tesouro", "GOAL", "100.00", false);

        int status = mockMvc.perform(patch("/api/v1/envelopes/{id}", envelopeId).header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("wealthAllocation", true, "version", 0))))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isIn(403, 404);
    }

    @Test
    @DisplayName("resumo do histórico sem sessão responde 401")
    void resumoSemSessao() throws Exception {
        mockMvc.perform(get("/api/v1/history/summary?from=2026-09-01&to=2026-09-30")).andExpect(status().isUnauthorized());
    }
}
