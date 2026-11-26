package br.com.controlegastos.investments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.investments.application.QuoteProvider;
import br.com.controlegastos.investments.application.QuoteResult;
import br.com.controlegastos.investments.application.StockQuoteRefresher;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.shared.money.Money;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Rodadas de cotação contra o PostgreSQL real, com o provedor simulado
 * (spec investments/stock-quotes, ADR-0035): falha sem tocar lotes, limpeza após
 * a exclusão da conta e logs só com contagens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@Import(FixedClockTestConfiguration.class)
class StockQuoteRefreshIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String TOKEN_VALUE = "token-secreto-da-brapi";
    private static final Instant MARKET = Instant.parse("2026-09-15T14:30:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.cookie-name", () -> "refresh_token");
        registry.add("app.investments.quotes.brapi-token", () -> TOKEN_VALUE);
        // Rodadas só quando o teste chama; nunca pelo relógio durante a suíte.
        registry.add("app.investments.quotes.trading-cron", () -> "-");
        registry.add("app.investments.quotes.missing-cron", () -> "-");
    }

    @MockitoBean QuoteProvider provider;

    @Autowired StockQuoteRefresher refresher;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository authAttempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        authAttempts.deleteAll();
        jdbc.update("DELETE FROM stock_quote");
        jdbc.update("DELETE FROM stock_lot");
        when(provider.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("rodada com falhas mantém a cotação anterior e não altera nenhum lote")
    void failuresKeepQuoteAndLots() throws Exception {
        String titular = token("cot-falha@example.com");
        createLot(titular, "PETR4", 100, "38.47", "2026-09-03");
        createLot(titular, "VALE3", 2, "60.00", "2026-09-01");
        jdbc.update("INSERT INTO stock_quote VALUES ('PETR4', 41.20, '2026-09-14T20:00:00Z', '2026-09-14T20:01:00Z')");
        List<Map<String, Object>> lotsBefore = lotRows();
        when(provider.fetch(any())).thenReturn(new QuoteResult.Unavailable("io"));

        refresher.refreshHeld();

        assertThat(lotRows()).isEqualTo(lotsBefore);
        mockMvc.perform(get("/api/v1/investments/stocks").header("Authorization", "Bearer " + titular))
                .andExpect(jsonPath("$[1].ticker").value("PETR4"))
                .andExpect(jsonPath("$[1].currentQuote.price.amount").value("41.20"))
                .andExpect(jsonPath("$[1].currentQuote.quotedAt").value("2026-09-14T20:00:00Z"))
                .andExpect(jsonPath("$[1].acquisitionCost.amount").value("3847.00"))
                .andExpect(jsonPath("$[0].currentQuote").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("rodada grava a cotação com o instante de mercado e a listagem passa a mostrá-la")
    void successfulRunIsVisibleInListing() throws Exception {
        String titular = token("cot-sucesso@example.com");
        createLot(titular, "ITUB4", 10, "30.00", "2026-09-03");
        when(provider.fetch(B3Ticker.of("ITUB4"))).thenReturn(new QuoteResult.Quoted(Money.brl("33.33"), MARKET));

        refresher.refreshMissing();

        mockMvc.perform(get("/api/v1/investments/stocks").header("Authorization", "Bearer " + titular))
                .andExpect(jsonPath("$[0].currentQuote.price.amount").value("33.33"))
                .andExpect(jsonPath("$[0].currentQuote.quotedAt").value("2026-09-15T14:30:00Z"))
                .andExpect(jsonPath("$[0].currentValue.amount").value("333.30"));
    }

    @Test
    @DisplayName("exclusão da conta do único dono de MGLU3 faz a próxima rodada apagar a cotação")
    void accountErasureLeavesNoOrphanQuote() throws Exception {
        String titular = token("cot-orfa@example.com");
        createLot(titular, "MGLU3", 50, "9.00", "2026-09-03");
        jdbc.update("INSERT INTO stock_quote VALUES ('MGLU3', 9.10, '2026-09-14T20:00:00Z', '2026-09-14T20:01:00Z')");

        MvcResult export = mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + titular))
                .andExpect(status().isOk()).andReturn();
        assertThat(export.getResponse().getContentAsString()).doesNotContain("stockQuote").doesNotContain("9.10");

        mockMvc.perform(delete("/api/v1/privacy/account").header("Authorization", "Bearer " + titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        refresher.refreshHeld();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_quote WHERE ticker = 'MGLU3'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("excluir pela API o último lote de MGLU3 faz a próxima rodada apagar a cotação")
    void lastLotDeletedRemovesQuote() throws Exception {
        String titular = token("cot-ultimo-lote@example.com");
        MvcResult created = mockMvc.perform(post("/api/v1/investments/stocks")
                        .header("Authorization", "Bearer " + titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticker", "MGLU3", "quantity", 50,
                                "unitPrice", Map.of("amount", "9.00", "currency", "BRL"),
                                "purchaseDate", "2026-09-03"))))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asString();
        jdbc.update("INSERT INTO stock_quote VALUES ('MGLU3', 9.10, '2026-09-14T20:00:00Z', '2026-09-14T20:01:00Z')");

        mockMvc.perform(delete("/api/v1/investments/stocks/" + id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNoContent());
        refresher.refreshHeld();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_quote WHERE ticker = 'MGLU3'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("log da rodada traz só contagens e códigos, sem ticker, preço ou token")
    void runLogHasCountsOnly(CapturedOutput output) throws Exception {
        String titular = token("cot-log@example.com");
        createLot(titular, "PETR4", 100, "38.47", "2026-09-03");
        createLot(titular, "ABCD3", 1, "10.00", "2026-09-03");
        createLot(titular, "VALE3", 1, "60.00", "2026-09-03");
        when(provider.fetch(B3Ticker.of("PETR4"))).thenReturn(new QuoteResult.Quoted(Money.brl("41.27"), MARKET));
        when(provider.fetch(B3Ticker.of("ABCD3"))).thenReturn(new QuoteResult.NotFound());
        when(provider.fetch(B3Ticker.of("VALE3"))).thenReturn(new QuoteResult.Unavailable("http_500"));
        int start = output.getAll().length();

        refresher.refreshHeld();

        String runLog = output.getAll().substring(start);
        assertThat(runLog).contains("quote_refresh mode=held tickers=3 updated=1 not_found=1 failed=1")
                .contains("failure_codes=[http_500]")
                .doesNotContain("PETR4").doesNotContain("ABCD3").doesNotContain("VALE3")
                .doesNotContain("41.27").doesNotContain(TOKEN_VALUE);
    }

    private List<Map<String, Object>> lotRows() {
        return jdbc.queryForList("SELECT id, ticker, quantity, unit_price, purchase_date, created_at FROM stock_lot ORDER BY id");
    }

    private void createLot(String token, String ticker, long quantity, String unitPrice, String purchaseDate)
            throws Exception {
        mockMvc.perform(post("/api/v1/investments/stocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticker", ticker, "quantity", quantity,
                                "unitPrice", Map.of("amount", unitPrice, "currency", "BRL"),
                                "purchaseDate", purchaseDate))))
                .andExpect(status().isCreated());
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
