package br.com.controlegastos.investments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Contrato HTTP do resumo da carteira (spec investments/portfolio-summary).
 * Relógio fixo: 2026-11-26 em America/Sao_Paulo (mesmo do CDB).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PortfolioSummaryApiIntegrationTest.SummaryFixedClock.class)
class PortfolioSummaryApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String SUMMARY = "/api/v1/investments/summary";
    private static final String STOCKS = "/api/v1/investments/stocks";
    private static final String CDBS = "/api/v1/investments/cdbs";

    @TestConfiguration(proxyBeanMethods = false)
    static class SummaryFixedClock {
        /** 2026-11-27 02:00 UTC = 2026-11-26 23:00 America/Sao_Paulo. */
        static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

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
    @DisplayName("carteira vazia responde complete true com totais zero e listas vazias")
    void emptyPortfolio() throws Exception {
        String titular = token("resumo-vazio@example.com");

        summary(titular)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.totalCost.amount").value("0.00"))
                .andExpect(jsonPath("$.totalCost.currency").value("BRL"))
                .andExpect(jsonPath("$.totalCurrent.amount").value("0.00"))
                .andExpect(jsonPath("$.totalPnl.amount").value("0.00"))
                .andExpect(jsonPath("$.stocks.length()").value(0))
                .andExpect(jsonPath("$.cdbs.length()").value(0));
    }

    @Test
    @DisplayName("dois lotes PETR4 agregam com averagePrice 38.98 e pnl 333.00")
    void aggregatesPetr4WithAveragePrice() throws Exception {
        String titular = token("resumo-petr@example.com");
        createStock(titular, lot("PETR4", 100, "38.47", "2026-11-03"));
        createStock(titular, lot("PETR4", 50, "40.00", "2026-11-10"));
        quote("PETR4", "41.20", "2026-11-26T20:00:00Z");

        summary(titular)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.stocks[0].ticker").value("PETR4"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(150))
                .andExpect(jsonPath("$.stocks[0].averagePrice.amount").value("38.98"))
                .andExpect(jsonPath("$.stocks[0].acquisitionCost.amount").value("5847.00"))
                .andExpect(jsonPath("$.stocks[0].currentValue.amount").value("6180.00"))
                .andExpect(jsonPath("$.stocks[0].pnl.amount").value("333.00"))
                .andExpect(jsonPath("$.stocks[0].lots.length()").value(2))
                .andExpect(jsonPath("$.stocks[0].lots[0].currentValue.amount").value("4120.00"))
                .andExpect(jsonPath("$.stocks[0].lots[0].pnl.amount").value("273.00"))
                .andExpect(jsonPath("$.stocks[0].lots[1].currentValue.amount").value("2060.00"))
                .andExpect(jsonPath("$.stocks[0].lots[1].pnl.amount").value("60.00"))
                .andExpect(jsonPath("$.totalCost.amount").value("5847.00"))
                .andExpect(jsonPath("$.totalCurrent.amount").value("6180.00"))
                .andExpect(jsonPath("$.totalPnl.amount").value("333.00"));
    }

    @Test
    @DisplayName("lote sem cotação traz nulls e complete false; totais só do cotado")
    void unquotedLotHasNullsAndIncompleteTotals() throws Exception {
        String titular = token("resumo-parcial@example.com");
        createStock(titular, lot("PETR4", 100, "38.47", "2026-11-03"));
        createStock(titular, lot("VALE3", 20, "100.00", "2026-11-01"));
        quote("PETR4", "41.20", "2026-11-26T20:00:00Z");

        summary(titular)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.totalCost.amount").value("3847.00"))
                .andExpect(jsonPath("$.totalCurrent.amount").value("4120.00"))
                .andExpect(jsonPath("$.totalPnl.amount").value("273.00"))
                .andExpect(jsonPath("$.stocks.length()").value(2))
                .andExpect(jsonPath("$.stocks[0].ticker").value("PETR4"))
                .andExpect(jsonPath("$.stocks[1].ticker").value("VALE3"))
                .andExpect(jsonPath("$.stocks[1].acquisitionCost.amount").value("2000.00"))
                .andExpect(jsonPath("$.stocks[1].averagePrice.amount").value("100.00"))
                .andExpect(jsonPath("$.stocks[1].currentValue").value(nullValue()))
                .andExpect(jsonPath("$.stocks[1].pnl").value(nullValue()))
                .andExpect(jsonPath("$.stocks[1].lots[0].currentValue").value(nullValue()))
                .andExpect(jsonPath("$.stocks[1].lots[0].pnl").value(nullValue()));
    }

    @Test
    @DisplayName("CDB entra no resumo com estimatedValue e pnl 1250.00")
    void cdbContributesEstimatedValueAndPnl() throws Exception {
        String titular = token("resumo-cdb@example.com");
        createCdb(titular, cdb("10000.00", "0.1250", "2025-11-26", "2027-11-26"));

        summary(titular)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.cdbs.length()").value(1))
                .andExpect(jsonPath("$.cdbs[0].principal.amount").value("10000.00"))
                .andExpect(jsonPath("$.cdbs[0].estimatedValue.amount").value("11250.00"))
                .andExpect(jsonPath("$.cdbs[0].pnl.amount").value("1250.00"))
                .andExpect(jsonPath("$.totalCost.amount").value("10000.00"))
                .andExpect(jsonPath("$.totalCurrent.amount").value("11250.00"))
                .andExpect(jsonPath("$.totalPnl.amount").value("1250.00"));
    }

    @Test
    @DisplayName("totais somam ações cotadas e CDB; complete true")
    void completeSummaryWithStocksAndCdbs() throws Exception {
        String titular = token("resumo-completo@example.com");
        createStock(titular, lot("PETR4", 100, "38.47", "2026-11-03"));
        quote("PETR4", "41.20", "2026-11-26T20:00:00Z");
        createCdb(titular, cdb("10000.00", "0.1250", "2025-11-26", "2027-11-26"));

        summary(titular)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.totalCost.amount").value("13847.00"))
                .andExpect(jsonPath("$.totalCurrent.amount").value("15370.00"))
                .andExpect(jsonPath("$.totalPnl.amount").value("1523.00"));
    }

    @Test
    @DisplayName("consultar o resumo não cria ledger_entry nem altera saldo de verba")
    void summaryDoesNotTouchTheBudget() throws Exception {
        String titular = token("resumo-orcamento@example.com");
        createEnvelope(titular, "Investir", "1000.00");
        createStock(titular, lot("PETR4", 10, "30.00", "2026-11-01"));
        createCdb(titular, cdb("1000.00", "0.1000", "2026-01-01", "2027-01-01"));

        summary(titular).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ledger_entry e JOIN user_account u ON u.id = e.owner_id WHERE u.email_normalized = ?",
                Integer.class, "resumo-orcamento@example.com")).isZero();
        mockMvc.perform(get("/api/v1/ledger/summary").header("Authorization", "Bearer " + titular)
                        .param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1000.00"));
    }

    @Test
    @DisplayName("sem token é 401; titular B e participante não veem posições de A")
    void onlyTitularSeesOwnSummary() throws Exception {
        String titular = verifiedToken("resumo-auth-dono@example.com");
        String participant = verifiedToken("resumo-auth-part@example.com");
        String stranger = token("resumo-auth-estranho@example.com");
        String envelopeId = createEnvelope(titular, "Casa", "3000.00");
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "resumo-auth-part@example.com"))))
                .andExpect(status().isCreated());
        createStock(titular, lot("PETR4", 100, "38.47", "2026-11-03"));
        quote("PETR4", "41.20", "2026-11-26T20:00:00Z");
        createCdb(titular, cdb("10000.00", "0.1250", "2025-11-26", "2027-11-26"));

        mockMvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());

        summary(stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(0))
                .andExpect(jsonPath("$.cdbs.length()").value(0))
                .andExpect(jsonPath("$.totalCost.amount").value("0.00"));

        summary(participant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(0))
                .andExpect(jsonPath("$.cdbs.length()").value(0))
                .andExpect(jsonPath("$.totalCost.amount").value("0.00"));

        summary(titular)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.cdbs.length()").value(1))
                .andExpect(jsonPath("$.totalPnl.amount").value("1523.00"));
    }

    private ResultActions summary(String token) throws Exception {
        return mockMvc.perform(get(SUMMARY).header("Authorization", "Bearer " + token));
    }

    private void createStock(String token, Map<String, Object> body) throws Exception {
        mockMvc.perform(post(STOCKS).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private void createCdb(String token, Map<String, Object> body) throws Exception {
        mockMvc.perform(post(CDBS).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private static Map<String, Object> lot(String ticker, long quantity, String unitPrice, String purchaseDate) {
        return Map.of("ticker", ticker, "quantity", quantity,
                "unitPrice", Map.of("amount", unitPrice, "currency", "BRL"), "purchaseDate", purchaseDate);
    }

    private static Map<String, Object> cdb(String principal, String annualRate, String start, String maturity) {
        return Map.of(
                "principal", Map.of("amount", principal, "currency", "BRL"),
                "annualRate", annualRate,
                "startDate", start,
                "maturityDate", maturity);
    }

    private void quote(String ticker, String price, String quotedAt) {
        jdbc.update("INSERT INTO stock_quote (ticker, price, quoted_at, fetched_at) VALUES (?, ?::numeric, ?::timestamptz, now()) "
                + "ON CONFLICT (ticker) DO UPDATE SET price = EXCLUDED.price, quoted_at = EXCLUDED.quoted_at",
                ticker, price, quotedAt);
    }

    private String createEnvelope(String token, String name, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", "LIMIT",
                                "baseAmount", Map.of("amount", baseAmount, "currency", "BRL")))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String verifiedToken(String email) throws Exception {
        String token = token(email);
        jdbc.update("UPDATE user_account SET email_verified_at = now() WHERE email_normalized = ?", email);
        return token;
    }

    private String token(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }
}
