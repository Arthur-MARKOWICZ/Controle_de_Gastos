package br.com.controlegastos.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class LedgerSummaryUsagePctApiIntegrationTest {

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
    void returnsUsagePercentageWithTwoDecimalPlacesForExactDivision() throws Exception {
        String token = tokenFor("percentual-exato@example.com");
        configureIncome(token, "1000.00");
        createEnvelope(token, "Moradia", "500.00");

        JsonNode usagePct = summaryUsagePct(token);

        assertThat(usagePct.isNumber()).isTrue();
        assertThat(usagePct.decimalValue()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void returnsZeroUsagePercentageWhenIncomeIsZero() throws Exception {
        String token = tokenFor("percentual-renda-zero@example.com");
        configureIncome(token, "0.00");

        JsonNode usagePct = summaryUsagePct(token);

        assertThat(usagePct.isNumber()).isTrue();
        assertThat(usagePct.asText()).isEqualTo("0");
    }

    @Test
    void roundsUsagePercentageToTwoDecimalPlacesWithHalfUp() throws Exception {
        String token = tokenFor("percentual-arredondado@example.com");
        configureIncome(token, "3.00");
        createEnvelope(token, "Reserva", "1.00");

        JsonNode usagePct = summaryUsagePct(token);

        assertThat(usagePct.isNumber()).isTrue();
        assertThat(usagePct.asText()).isEqualTo("33.33");
    }

    private JsonNode summaryUsagePct(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ledger/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("usagePct");
    }

    private void configureIncome(String token, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk());
    }

    private void createEnvelope(String token, String name, String baseAmount) throws Exception {
        mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "purpose", "LIMIT",
                                "baseAmount", money(baseAmount)))))
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
