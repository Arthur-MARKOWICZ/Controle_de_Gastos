package br.com.controlegastos.investments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Contrato HTTP dos lotes de ativos da B3 (spec investments/stock-lots, ADR-0034).
 * Relógio fixo: 2026-09-15 em America/Sao_Paulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class StockLotApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String STOCKS = "/api/v1/investments/stocks";

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
    @DisplayName("titular cadastra um lote e recebe o custo de aquisição")
    void registersLotWithAcquisitionCost() throws Exception {
        String titular = token("lote-cria@example.com");

        create(titular, lot(" petr4f ", 100, "38.47", "2026-09-03"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ticker").value("PETR4"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.unitPrice.amount").value("38.47"))
                .andExpect(jsonPath("$.unitPrice.currency").value("BRL"))
                .andExpect(jsonPath("$.acquisitionCost.amount").value("3847.00"))
                .andExpect(jsonPath("$.acquisitionCost.currency").value("BRL"))
                .andExpect(jsonPath("$.purchaseDate").value("2026-09-03"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("segunda compra do mesmo ticker vira outro lote; unit, ETF, FII e BDR são aceitos")
    void repeatedTickerAndOtherAssetKinds() throws Exception {
        String titular = token("lote-repete@example.com");
        String first = idOf(create(titular, lot("PETR4", 10, "30.00", "2026-09-01")));
        String second = idOf(create(titular, lot("PETR4", 5, "31.00", "2026-09-02")));
        for (String ticker : new String[] {"TAEE11", "BOVA11", "MXRF11", "AAPL34"}) {
            create(titular, lot(ticker, 1, "10.00", "2026-09-10")).andExpect(status().isCreated());
        }

        assertThat(first).isNotEqualTo(second);
        list(titular).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].ticker").value("PETR4"))
                .andExpect(jsonPath("$[1].ticker").value("PETR4"));
    }

    @Test
    @DisplayName("ticker, quantidade, preço e data inválidos respondem 400 sem criar o lote")
    void invalidInputIsRejected() throws Exception {
        String titular = token("lote-invalido@example.com");

        for (String ticker : new String[] {"PETR", "PETR123", "PE4", "PETR-4"}) {
            create(titular, lot(ticker, 1, "10.00", "2026-09-01")).andExpect(status().isBadRequest());
        }
        for (String price : new String[] {"0.00", "-1.00", "38.475"}) {
            create(titular, lot("PETR4", 1, price, "2026-09-01")).andExpect(status().isBadRequest());
        }
        create(titular, lot("PETR4", 0, "10.00", "2026-09-01")).andExpect(status().isBadRequest());
        create(titular, lot("PETR4", -5, "10.00", "2026-09-01")).andExpect(status().isBadRequest());
        for (String quantity : new String[] {"1.5", "\"100\""}) {
            createRaw(titular, """
                    {"ticker":"PETR4","quantity":%s,"unitPrice":{"amount":"10.00","currency":"BRL"},
                     "purchaseDate":"2026-09-01"}""".formatted(quantity)).andExpect(status().isBadRequest());
        }
        create(titular, lot("PETR4", 1, "10.00", "2026-09-16")).andExpect(status().isBadRequest());
        create(titular, lot("PETR4", 1, "10.00", "15/09/2026")).andExpect(status().isBadRequest());
        createRaw(titular, "{\"ticker\":\"PETR4\"}").andExpect(status().isBadRequest());

        list(titular).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("compra feita hoje é aceita")
    void purchaseTodayIsAccepted() throws Exception {
        String titular = token("lote-hoje@example.com");

        create(titular, lot("VALE3", 1, "60.00", "2026-09-15")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("listagem ordena por data da compra e depois por ticker; sem lotes é vazia")
    void listingOrder() throws Exception {
        String titular = token("lote-ordem@example.com");
        list(titular).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        create(titular, lot("VALE3", 1, "60.00", "2026-09-10"));
        create(titular, lot("ITUB4", 1, "35.00", "2026-08-15"));
        create(titular, lot("BBAS3", 1, "28.00", "2026-08-15"));

        list(titular).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("BBAS3"))
                .andExpect(jsonPath("$[1].ticker").value("ITUB4"))
                .andExpect(jsonPath("$[2].ticker").value("VALE3"))
                .andExpect(jsonPath("$[2].acquisitionCost.amount").value("60.00"));
    }

    @Test
    @DisplayName("titular exclui o próprio lote; id desconhecido responde 404")
    void deletesOwnLot() throws Exception {
        String titular = token("lote-exclui@example.com");
        String id = idOf(create(titular, lot("PETR4", 1, "10.00", "2026-09-01")));

        mockMvc.perform(delete(STOCKS + "/{id}", id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNoContent());
        list(titular).andExpect(jsonPath("$.length()").value(0));
        assertThat(lotCount(id)).isZero();

        mockMvc.perform(delete(STOCKS + "/{id}", id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_LOT_NOT_FOUND"));
    }

    @Test
    @DisplayName("outro usuário e participante de verba não veem nem excluem o lote; sem token é 401")
    void onlyTitularReachesLots() throws Exception {
        String titular = verifiedToken("lote-auth-dono@example.com");
        String participant = verifiedToken("lote-auth-part@example.com");
        String stranger = token("lote-auth-estranho@example.com");
        String envelopeId = createEnvelope(titular, "Casa", "3000.00");
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "lote-auth-part@example.com"))))
                .andExpect(status().isCreated());
        String id = idOf(create(titular, lot("PETR4", 1, "10.00", "2026-09-01")));

        for (String other : new String[] {participant, stranger}) {
            list(other).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            mockMvc.perform(delete(STOCKS + "/{id}", id).header("Authorization", "Bearer " + other))
                    .andExpect(status().isNotFound());
        }
        assertThat(lotCount(id)).isEqualTo(1);

        mockMvc.perform(get(STOCKS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(STOCKS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lot("PETR4", 1, "10.00", "2026-09-01"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(STOCKS + "/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cadastrar e excluir lote não cria lançamento nem muda saldo de verba")
    void lotsDoNotTouchTheBudget() throws Exception {
        String titular = token("lote-orcamento@example.com");
        createEnvelope(titular, "Investir", "1000.00");

        String id = idOf(create(titular, lot("PETR4", 10, "30.00", "2026-09-01")));
        mockMvc.perform(delete(STOCKS + "/{id}", id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ledger_entry e JOIN user_account u ON u.id = e.owner_id WHERE u.email_normalized = ?",
                Integer.class, "lote-orcamento@example.com")).isZero();
        mockMvc.perform(get("/api/v1/ledger/summary").header("Authorization", "Bearer " + titular).param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1000.00"));
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return createRaw(token, objectMapper.writeValueAsString(body));
    }

    private ResultActions createRaw(String token, String body) throws Exception {
        return mockMvc.perform(post(STOCKS).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(String token) throws Exception {
        return mockMvc.perform(get(STOCKS).header("Authorization", "Bearer " + token));
    }

    private static Map<String, Object> lot(String ticker, long quantity, String unitPrice, String purchaseDate) {
        return Map.of("ticker", ticker, "quantity", quantity,
                "unitPrice", Map.of("amount", unitPrice, "currency", "BRL"), "purchaseDate", purchaseDate);
    }

    private String idOf(ResultActions result) throws Exception {
        MvcResult created = result.andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private int lotCount(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM stock_lot WHERE id = ?", Integer.class, UUID.fromString(id));
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
