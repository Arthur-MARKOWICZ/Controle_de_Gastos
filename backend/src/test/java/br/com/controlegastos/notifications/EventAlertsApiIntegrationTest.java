package br.com.controlegastos.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.time.Duration;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Alertas gerados por eventos do ledger (spec notifications/event-alerts). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class EventAlertsApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final Duration WAIT = Duration.ofSeconds(10);

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
    @DisplayName("verba que fica negativa gera um único alerta ativo, resolvido quando o saldo volta")
    void negativeBalanceLifecycle() throws Exception {
        String owner = token("evento-negativo@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "100.00");

        String first = expense(owner, envelopeId, "125.00");
        awaitAlerts(owner, "NEGATIVE_BALANCE", 1);

        String second = expense(owner, envelopeId, "10.00");
        Thread.sleep(300);
        assertThat(activeAlerts(owner, "NEGATIVE_BALANCE")).isEqualTo(1);

        deleteExpense(owner, first);
        deleteExpense(owner, second);
        awaitAlerts(owner, "NEGATIVE_BALANCE", 0);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM in_app_alert WHERE resolved_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("gasto de participante gera alerta SHARED_EXPENSE só para o dono, sem dados do participante")
    void sharedExpenseAlertsOnlyTheOwner() throws Exception {
        String owner = verifiedToken("evento-dono@example.com");
        String participant = verifiedToken("evento-participante@example.com");
        String envelopeId = createEnvelope(owner, "Casa", "1000.00");
        invite(owner, envelopeId, "evento-participante@example.com");

        expense(participant, envelopeId, "40.00");

        awaitAlerts(owner, "SHARED_EXPENSE", 1);
        assertThat(activeAlerts(participant, "SHARED_EXPENSE")).isZero();
        String body = alertsJson(owner);
        assertThat(body).doesNotContain("40.00").doesNotContain("evento-participante");
    }

    @Test
    @DisplayName("gasto registrado pelo próprio dono em verba compartilhada não gera SHARED_EXPENSE")
    void ownerExpenseDoesNotAlert() throws Exception {
        String owner = verifiedToken("evento-dono2@example.com");
        verifiedToken("evento-participante2@example.com");
        String envelopeId = createEnvelope(owner, "Casa", "1000.00");
        invite(owner, envelopeId, "evento-participante2@example.com");

        expense(owner, envelopeId, "40.00");

        Thread.sleep(500);
        assertThat(activeAlerts(owner, "SHARED_EXPENSE")).isZero();
    }

    @Test
    @DisplayName("gasto que não deixa a verba negativa não gera alerta de saldo negativo")
    void gastoDentroDoSaldoNaoAlerta() throws Exception {
        String owner = token("evento-dentro@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "100.00");

        expense(owner, envelopeId, "100.00");

        Thread.sleep(500);
        assertThat(activeAlerts(owner, "NEGATIVE_BALANCE")).isZero();
    }

    @Test
    @DisplayName("terceiro sem vínculo não lança na verba e o dono não recebe alerta nenhum")
    void terceiroNaoLancaNemGeraAlerta() throws Exception {
        String owner = verifiedToken("evento-dono3@example.com");
        String stranger = verifiedToken("evento-terceiro3@example.com");
        String envelopeId = createEnvelope(owner, "Casa", "10.00");

        mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody("50.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENVELOPE_NOT_FOUND"));

        Thread.sleep(500);
        assertThat(objectMapper.readTree(alertsJson(owner)).get("items").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entry WHERE envelope_id = ?::uuid",
                Integer.class, envelopeId)).isZero();
    }

    @Test
    @DisplayName("alerta do dono não aparece na lista de alertas de outro titular")
    void alertaDoDonoNaoVazaParaTerceiro() throws Exception {
        String owner = token("evento-dono4@example.com");
        String stranger = token("evento-terceiro4@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "100.00");

        expense(owner, envelopeId, "150.00");
        awaitAlerts(owner, "NEGATIVE_BALANCE", 1);

        String body = alertsJson(stranger);
        assertThat(objectMapper.readTree(body).get("items").size()).isZero();
        assertThat(body).doesNotContain(envelopeId);
    }

    @Test
    @DisplayName("gasto com valor fora da escala de duas casas é recusado e não gera alerta")
    void valorComEscalaInvalidaRecusado() throws Exception {
        String owner = token("evento-escala@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "1.00");

        mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody("10.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        Thread.sleep(500);
        assertThat(activeAlerts(owner, "NEGATIVE_BALANCE")).isZero();
    }

    @Test
    @DisplayName("gasto em verba inexistente responde 404 ENVELOPE_NOT_FOUND")
    void gastoEmVerbaInexistente() throws Exception {
        String owner = token("evento-inexistente@example.com");

        mockMvc.perform(post("/api/v1/envelopes/{id}/entries", UUID.randomUUID())
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseBody("10.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENVELOPE_NOT_FOUND"));
    }

    @Test
    @DisplayName("listar alertas sem sessão responde 401")
    void listarAlertasSemSessao() throws Exception {
        mockMvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
    }

    private String expenseBody(String amount) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "kind", "EXPENSE", "amount", money(amount), "occurredAt", "2026-09-15"));
    }

    private void awaitAlerts(String token, String type, int expected) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(activeAlerts(token, type)).isEqualTo(expected));
    }

    private int activeAlerts(String token, String type) throws Exception {
        int count = 0;
        for (JsonNode item : objectMapper.readTree(alertsJson(token)).get("items")) {
            if (type.equals(item.get("type").asString())) {
                count++;
            }
        }
        return count;
    }

    private String alertsJson(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String expense(String token, String envelopeId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "EXPENSE", "amount", money(amount), "occurredAt", "2026-09-15"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private void deleteExpense(String token, String entryId) throws Exception {
        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId).header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
    }

    private void invite(String owner, String envelopeId, String email) throws Exception {
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());
    }

    private String createEnvelope(String token, String name, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", "LIMIT", "baseAmount", money(baseAmount)))))
                .andExpect(status().isCreated()).andReturn();
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

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }
}
