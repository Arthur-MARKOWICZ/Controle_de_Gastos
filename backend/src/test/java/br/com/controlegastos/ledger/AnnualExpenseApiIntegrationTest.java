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
class AnnualExpenseApiIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.cookie-name", () -> "refresh_token");
    }
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository authAttempts;
    @BeforeEach void resetRateLimits() { authAttempts.deleteAll(); }

    @Test
    void createsAnAnnualExpenseAndProvisionsItsTotalWithoutResettingTheBalance() throws Exception {
        String token = tokenFor("anual@example.com");
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "IPVA", "purpose", "ANNUAL_EXPENSE", "annualAmount", money("1000.01"),
                "dueMonth", 1, "dueDay", 10, "fundingMode", "MONTHLY"));

        mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.annualExpense.annualAmount.amount").value("1000.01"))
                .andExpect(jsonPath("$.annualExpense.dueMonth").value(1))
                .andExpect(jsonPath("$.baseAmount.amount").value("0.00"));

        mockMvc.perform(get("/api/v1/ledger/summary?month=2027-01").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1000.01"));
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", "frase segura de teste"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials)).andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return response.get("accessToken").asString();
    }

    private Map<String, String> money(String amount) { return Map.of("amount", amount, "currency", "BRL"); }

    private org.springframework.test.web.servlet.ResultActions createAnnual(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("gasto anual pago de uma vez não provisiona parcela mensal")
    void pagoDeUmaVezSemParcela() throws Exception {
        String token = tokenFor("anual-onetime@example.com");
        createAnnual(token, Map.of("name", "Seguro", "purpose", "ANNUAL_EXPENSE", "annualAmount", money("700.00"),
                "dueMonth", 11, "dueDay", 20, "fundingMode", "ONE_TIME"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/ledger/summary?month=2026-09").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.envelopes[0].baseAmount.amount").value("0.00"));
    }

    @Test
    @DisplayName("valor anual zero é recusado com 400")
    void valorAnualZeroRecusado() throws Exception {
        String token = tokenFor("anual-zero@example.com");

        createAnnual(token, Map.of("name", "IPVA", "purpose", "ANNUAL_EXPENSE", "annualAmount", money("0.00"),
                "dueMonth", 1, "dueDay", 10, "fundingMode", "MONTHLY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("modo de provisão desconhecido é recusado com 400")
    void modoDesconhecidoRecusado() throws Exception {
        String token = tokenFor("anual-mode@example.com");

        createAnnual(token, Map.of("name", "IPVA", "purpose", "ANNUAL_EXPENSE", "annualAmount", money("100.00"),
                "dueMonth", 1, "dueDay", 10, "fundingMode", "SEMANAL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("gasto anual sem vencimento é recusado com 400")
    void semVencimentoRecusado() throws Exception {
        String token = tokenFor("anual-nodue@example.com");

        createAnnual(token, Map.of("name", "IPVA", "purpose", "ANNUAL_EXPENSE", "annualAmount", money("100.00"),
                "fundingMode", "MONTHLY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("gasto anual de outro titular não aparece no meu resumo")
    void isoladoPorTitular() throws Exception {
        String owner = tokenFor("anual-owner@example.com");
        String other = tokenFor("anual-other@example.com");
        createAnnual(owner, Map.of("name", "IPVA", "purpose", "ANNUAL_EXPENSE", "annualAmount", money("100.00"),
                "dueMonth", 1, "dueDay", 10, "fundingMode", "MONTHLY")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/ledger/summary?month=2026-09").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.envelopes.length()").value(0));
    }

    @Test
    @DisplayName("criar gasto anual sem sessão responde 401")
    void criarSemSessao() throws Exception {
        mockMvc.perform(post("/api/v1/envelopes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
