package br.com.controlegastos.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
class ExpenseSuggestionsApiIntegrationTest {

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
    @DisplayName("GET /ledger/expense-suggestions devolve até 3 itens com MoneyJson")
    void returnsSuggestionsWithMoneyJson() throws Exception {
        String token = tokenFor("suggestions-owner@example.com");
        configureIncome(token, "5000.00");
        String almoco = createEnvelope(token, "Almoço", "500.00");
        registerExpense(token, almoco, "32.50", "2026-09-01");
        registerExpense(token, almoco, "32.50", "2026-09-08");
        registerExpense(token, almoco, "32.50", "2026-09-15");

        MvcResult result = mockMvc.perform(get("/api/v1/ledger/expense-suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").isString())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.get("items").size()).isBetween(1, 3);
        JsonNode first = body.get("items").get(0);
        assertThat(first.get("name").asString()).isEqualTo("Almoço");
        assertThat(first.get("suggestedAmount").get("amount").asString()).isEqualTo("32.50");
        assertThat(first.get("suggestedAmount").get("currency").asString()).isEqualTo("BRL");
        assertThat(first.get("envelopeId").asString()).isEqualTo(almoco);
    }

    @Test
    @DisplayName("sem histórico suficiente a lista vem vazia com 200")
    void emptyWhenInsufficientHistory() throws Exception {
        String token = tokenFor("suggestions-empty@example.com");
        configureIncome(token, "1000.00");
        createEnvelope(token, "Mercado", "200.00");

        mockMvc.perform(get("/api/v1/ledger/expense-suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("sem sessão responde 401")
    void unauthorizedWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/expense-suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("consulta bem-sucedida não coloca amount nem descrição nos logs")
    void successfulQueryLeavesNoAmountInLogs(CapturedOutput output) throws Exception {
        String token = tokenFor("suggestions-log@example.com");
        configureIncome(token, "5000.00");
        String almoco = createEnvelope(token, "Almoço Log", "500.00");
        registerExpense(token, almoco, "32.50", "2026-09-01", "Marmita do dia");
        registerExpense(token, almoco, "32.50", "2026-09-08", "Marmita do dia");

        int start = output.getAll().length();
        mockMvc.perform(get("/api/v1/ledger/expense-suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].suggestedAmount.amount").value("32.50"));

        String after = output.getAll().substring(start);
        assertThat(after).doesNotContain("32.50").doesNotContain("Marmita do dia");
    }

    @Test
    @DisplayName("após registrar gastos no padrão a consulta seguinte reflete a sugestão")
    void suggestionsReflectRegisteredPattern() throws Exception {
        String token = tokenFor("suggestions-reflect@example.com");
        configureIncome(token, "5000.00");
        String cafe = createEnvelope(token, "Café", "200.00");

        mockMvc.perform(get("/api/v1/ledger/expense-suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        registerExpense(token, cafe, "12.00", "2026-09-01");
        registerExpense(token, cafe, "12.00", "2026-09-08");
        registerExpense(token, cafe, "12.00", "2026-09-15");

        mockMvc.perform(get("/api/v1/ledger/expense-suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Café"))
                .andExpect(jsonPath("$.items[0].suggestedAmount.amount").value("12.00"));
    }

    private void configureIncome(String token, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk());
    }

    private String createEnvelope(String token, String name, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "purpose", "LIMIT",
                                "baseAmount", money(baseAmount)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private void registerExpense(String token, String envelopeId, String amount, String occurredAt) throws Exception {
        registerExpense(token, envelopeId, amount, occurredAt, null);
    }

    private void registerExpense(String token, String envelopeId, String amount, String occurredAt, String description)
            throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("kind", "EXPENSE");
        body.put("amount", money(amount));
        body.put("occurredAt", occurredAt);
        if (description != null) {
            body.put("description", description);
        }
        mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(new Credentials(email, PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private record Credentials(String email, String password) {}
}
