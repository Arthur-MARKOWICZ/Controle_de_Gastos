package br.com.controlegastos.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class HistorySearchApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String FROM = "2026-09-01";
    private static final String TO = "2026-09-30";

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
    void textSearchIsCaseInsensitiveAndIgnoresBlankTerm() throws Exception {
        String token = tokenFor("search-text@example.com");
        String envelope = createEnvelope(token, "Saúde", "500.00");
        createExpense(token, envelope, "35.90", "Farmácia São João");
        createExpense(token, envelope, "120.00", "Mercado");
        createExpense(token, envelope, "10.00", null);

        assertThat(descriptions(history(token, Map.of("q", "farmácia")))).containsExactly("Farmácia São João");
        assertThat(descriptions(history(token, Map.of("q", "FARMÁCIA")))).containsExactly("Farmácia São João");
        assertThat(history(token, Map.of("q", "   ")).get("items")).hasSize(3);
        assertThat(descriptions(history(token, Map.of("q", "mercado")))).containsExactly("Mercado");
    }

    @Test
    void wildcardsAreLiteral() throws Exception {
        String token = tokenFor("search-wildcard@example.com");
        String envelope = createEnvelope(token, "Compras", "500.00");
        createExpense(token, envelope, "5.00", "Desconto 10%");
        createExpense(token, envelope, "6.00", "Desconto 100 reais");
        createExpense(token, envelope, "7.00", "item_a");
        createExpense(token, envelope, "8.00", "itemXa");

        assertThat(descriptions(history(token, Map.of("q", "10%")))).containsExactly("Desconto 10%");
        assertThat(descriptions(history(token, Map.of("q", "item_a")))).containsExactly("item_a");
    }

    @Test
    void tooLongTermIsRejected() throws Exception {
        String token = tokenFor("search-long@example.com");
        mockMvc.perform(historyRequest(token, Map.of("q", "a".repeat(141))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(historyRequest(token, Map.of("q", "  " + "a".repeat(140) + "  ")))
                .andExpect(status().isOk());
    }

    @Test
    void envelopeFilterRespectsVisibility() throws Exception {
        String ownerToken = tokenFor("search-env-owner@example.com");
        UUID ownerId = userId("search-env-owner@example.com");
        String participantToken = tokenFor("search-env-participant@example.com");
        UUID participantId = userId("search-env-participant@example.com");
        String strangerToken = tokenFor("search-env-stranger@example.com");

        String market = createEnvelope(ownerToken, "Mercado", "500.00");
        String pharmacy = createEnvelope(ownerToken, "Farmácia", "500.00");
        share(pharmacy, ownerId, participantId);
        createExpense(ownerToken, market, "100.00", "Compra do mês");
        createExpense(ownerToken, pharmacy, "30.00", "Remédio");

        assertThat(descriptions(history(ownerToken, Map.of("envelopeId", pharmacy)))).containsExactly("Remédio");
        assertThat(descriptions(history(participantToken, Map.of("envelopeId", pharmacy)))).containsExactly("Remédio");

        JsonNode stranger = history(strangerToken, Map.of("envelopeId", pharmacy));
        assertThat(stranger.get("items")).isEmpty();
        assertThat(stranger.get("matchedTotal").get("amount").asString()).isEqualTo("0.00");

        mockMvc.perform(historyRequest(ownerToken, Map.of("envelopeId", "abc")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorFilterSeparatesMineFromOthers() throws Exception {
        String ownerToken = tokenFor("search-author-owner@example.com");
        UUID ownerId = userId("search-author-owner@example.com");
        String participantToken = tokenFor("search-author-participant@example.com");
        UUID participantId = userId("search-author-participant@example.com");

        String home = createEnvelope(ownerToken, "Casa", "500.00");
        share(home, ownerId, participantId);
        createExpense(ownerToken, home, "50.00", "Lançado pelo titular");
        createExpense(participantToken, home, "20.00", "Lançado pela participante");

        assertThat(descriptions(history(ownerToken, Map.of("author", "ME")))).containsExactly("Lançado pelo titular");
        assertThat(descriptions(history(ownerToken, Map.of("author", "OTHERS")))).containsExactly("Lançado pela participante");
        assertThat(descriptions(history(participantToken, Map.of("author", "ME")))).containsExactly("Lançado pela participante");

        mockMvc.perform(historyRequest(ownerToken, Map.of("author", "TODOS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchKeepsExistingVisibilityForDeletedAndStrangers() throws Exception {
        String ownerToken = tokenFor("search-vis-owner@example.com");
        UUID ownerId = userId("search-vis-owner@example.com");
        String participantToken = tokenFor("search-vis-participant@example.com");
        UUID participantId = userId("search-vis-participant@example.com");
        String strangerToken = tokenFor("search-vis-stranger@example.com");

        String home = createEnvelope(ownerToken, "Casa", "500.00");
        share(home, ownerId, participantId);
        String deleted = createExpense(ownerToken, home, "50.00", "Farmácia X");
        createExpense(ownerToken, home, "35.90", "Farmácia Y");
        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", deleted).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(history(strangerToken, Map.of("q", "farmácia")).get("items")).isEmpty();
        assertThat(descriptions(history(participantToken, Map.of("q", "farmácia", "includeDeleted", "true"))))
                .containsExactly("Farmácia Y");

        JsonNode ownerView = history(ownerToken, Map.of("q", "farmácia", "includeDeleted", "true"));
        assertThat(descriptions(ownerView)).containsExactlyInAnyOrder("Farmácia X", "Farmácia Y");
        assertThat(ownerView.get("matchedTotal").get("amount").asString()).isEqualTo("35.90");
    }

    @Test
    void historyWithoutNewFiltersIsUnchanged() throws Exception {
        String token = tokenFor("search-regression@example.com");
        String envelope = createEnvelope(token, "Geral", "500.00");
        createExpense(token, envelope, "1.00", "Um");
        createExpense(token, envelope, "2.00", "Dois");
        createExpense(token, envelope, "3.00", null);

        JsonNode page = history(token, Map.of("size", "2"));
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        assertThat(page.get("matchedTotal").get("amount").asString()).isEqualTo("6.00");
        assertThat(page.get("matchedTotal").get("currency").asString()).isEqualTo("BRL");
    }

    @Test
    void matchedTotalSumsAllPages() throws Exception {
        String token = tokenFor("search-total@example.com");
        String envelope = createEnvelope(token, "Saúde", "500.00");
        createExpense(token, envelope, "35.90", "Farmácia X");
        createExpense(token, envelope, "12.10", "Farmácia X");
        createExpense(token, envelope, "200.00", "Mercado");

        JsonNode page = history(token, Map.of("q", "farmácia x", "size", "1"));
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        assertThat(page.get("matchedTotal").get("amount").asString()).isEqualTo("48.00");

        JsonNode none = history(token, Map.of("q", "inexistente"));
        assertThat(none.get("items")).isEmpty();
        assertThat(none.get("matchedTotal").get("amount").asString()).isEqualTo("0.00");
    }

    private JsonNode history(String token, Map<String, String> params) throws Exception {
        MvcResult result = mockMvc.perform(historyRequest(token, params)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private MockHttpServletRequestBuilder historyRequest(String token, Map<String, String> params) {
        Map<String, String> all = new HashMap<>(Map.of("from", FROM, "to", TO));
        all.putAll(params);
        MockHttpServletRequestBuilder request = get("/api/v1/history").header("Authorization", "Bearer " + token);
        all.forEach(request::param);
        return request;
    }

    private List<String> descriptions(JsonNode page) {
        List<String> values = new ArrayList<>();
        page.get("items").forEach(item -> values.add(item.get("entry").get("description").asString()));
        return values;
    }

    private void share(String envelopeId, UUID ownerId, UUID participantId) {
        jdbc.update(
                "INSERT INTO envelope_participant(envelope_id, user_id, added_at, added_by) VALUES (?, ?, ?, ?)",
                UUID.fromString(envelopeId), participantId, Timestamp.from(Instant.now()), ownerId);
    }

    private String createEnvelope(String token, String name, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", "LIMIT", "baseAmount", money(baseAmount)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String createExpense(String token, String envelopeId, String amount, String description) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "kind", "EXPENSE",
                "amount", money(amount),
                "occurredAt", "2026-09-10"));
        if (description != null) body.put("description", description);
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(new Credentials(email, PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
    }

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private record Credentials(String email, String password) {}
}
