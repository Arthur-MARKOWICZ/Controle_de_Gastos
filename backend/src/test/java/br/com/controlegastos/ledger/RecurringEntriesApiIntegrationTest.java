package br.com.controlegastos.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.ledger.application.RecurringOccurrenceGenerator;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.HashMap;
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
 * Contrato HTTP de recorrências (spec ledger/recurring-entries, ADR-0031).
 * Relógio fixo: 2026-09-15 em America/Sao_Paulo; recorrências do dia 15 vencem "hoje".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class RecurringEntriesApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String RECURRING = "/api/v1/envelopes/{id}/recurring-entries";
    private static final String PENDING = "/api/v1/recurring-occurrences/pending";

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
    @Autowired RecurringOccurrenceGenerator generator;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    @DisplayName("dono cria, a geração é idempotente e a pendente não altera o saldo")
    void generationIsIdempotentAndPendingDoesNotTouchBalance() throws Exception {
        String owner = token("rec-idem@example.com");
        String envelopeId = createEnvelope(owner, "Moradia", "LIMIT", "2000.00");
        ResultActions created = createRecurring(owner, envelopeId, "EXPENSE", "1800.00", 15, "Aluguel")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.amount.amount").value("1800.00"))
                .andExpect(jsonPath("$.dayOfMonth").value(15))
                .andExpect(jsonPath("$.firstCompetence").value("2026-09"));
        String recurringId = idOf(created);

        generator.generateDue();
        generator.generateDue();

        assertThat(occurrenceCount(recurringId)).isEqualTo(1);
        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.length()").value(1))
                .andExpect(jsonPath("$.current[0].envelopeName").value("Moradia"))
                .andExpect(jsonPath("$.current[0].description").value("Aluguel"))
                .andExpect(jsonPath("$.current[0].suggestedAmount.amount").value("1800.00"))
                .andExpect(jsonPath("$.current[0].dueDate").value("2026-09-15"))
                .andExpect(jsonPath("$.current[0].competence").value("2026-09"))
                .andExpect(jsonPath("$.overdue.length()").value(0));
        expectAvailable(owner, "2000.00");
    }

    @Test
    @DisplayName("recorrência criada depois do dia previsto começa no mês seguinte")
    void recurrenceAfterDueDayStartsNextMonth() throws Exception {
        String owner = token("rec-next@example.com");
        String envelopeId = createEnvelope(owner, "Internet", "LIMIT", "200.00");
        createRecurring(owner, envelopeId, "EXPENSE", "99.90", 5, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstCompetence").value("2026-10"));

        generator.generateDue();

        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.current.length()").value(0));
    }

    @Test
    @DisplayName("confirmar com valor ajustado cria um lançamento; segunda confirmação é 409")
    void confirmWithAdjustedAmount() throws Exception {
        String owner = token("rec-confirm@example.com");
        String envelopeId = createEnvelope(owner, "Energia", "LIMIT", "300.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "200.00", 15, "Luz"));
        generator.generateDue();
        String occurrenceId = firstPendingId(owner);

        confirm(owner, occurrenceId, Map.of("amount", money("237.45")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.ledgerEntryId").isNotEmpty());

        expectAvailable(owner, "62.55");
        mockMvc.perform(get("/api/v1/envelopes/{id}/entries", envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amount.amount").value("237.45"))
                .andExpect(jsonPath("$.items[0].occurredAt").value("2026-09-15"))
                .andExpect(jsonPath("$.items[0].description").value("Luz"));
        mockMvc.perform(get(RECURRING, envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$[0].amount.amount").value("200.00"));

        confirm(owner, occurrenceId, Map.of()).andExpect(status().isConflict());
        generator.generateDue();
        assertThat(occurrenceCount(recurringId)).isEqualTo(1);
        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.current.length()").value(0));
    }

    @Test
    @DisplayName("confirmar que deixa a verba negativa gera o mesmo alerta do lançamento manual")
    void confirmationTriggersNegativeBalanceAlert() throws Exception {
        String owner = token("rec-alert@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "LIMIT", "100.00");
        createRecurring(owner, envelopeId, "EXPENSE", "150.00", 15, "Feira");
        generator.generateDue();

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.items.length()").value(0));

        confirm(owner, firstPendingId(owner), Map.of()).andExpect(status().isOk());

        expectAvailable(owner, "-50.00");
        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NEGATIVE_BALANCE"))
                .andExpect(jsonPath("$.items[0].envelopeName").value("Mercado"));
    }

    @Test
    @DisplayName("descartar não cria lançamento, impede confirmação e não regenera")
    void discard() throws Exception {
        String owner = token("rec-discard@example.com");
        String envelopeId = createEnvelope(owner, "Streaming", "LIMIT", "100.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "39.90", 15, "Streaming"));
        generator.generateDue();
        String occurrenceId = firstPendingId(owner);

        mockMvc.perform(post("/api/v1/recurring-occurrences/{id}/discard", occurrenceId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        confirm(owner, occurrenceId, Map.of()).andExpect(status().isConflict());
        generator.generateDue();
        assertThat(occurrenceCount(recurringId)).isEqualTo(1);
        expectAvailable(owner, "100.00");
    }

    @Test
    @DisplayName("data futura na confirmação é 400 e a pendência continua")
    void futureDateIsRejected() throws Exception {
        String owner = token("rec-future@example.com");
        String envelopeId = createEnvelope(owner, "Moradia", "LIMIT", "2000.00");
        createRecurring(owner, envelopeId, "EXPENSE", "1800.00", 15, null);
        generator.generateDue();
        String occurrenceId = firstPendingId(owner);

        confirm(owner, occurrenceId, Map.of("occurredAt", "2026-09-30")).andExpect(status().isBadRequest());

        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.current.length()").value(1));
    }

    @Test
    @DisplayName("vencida continua listada e, confirmada, mantém a data original")
    void overdueKeepsOriginalDate() throws Exception {
        String owner = token("rec-overdue@example.com");
        String envelopeId = createEnvelope(owner, "Internet", "LIMIT", "200.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "99.90", 15, "Internet"));
        UUID occurrenceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recurring_occurrence (id, recurring_entry_id, envelope_id, competence, due_date,
                    suggested_amount, status, created_at, version)
                VALUES (?, ?, ?, DATE '2026-08-01', DATE '2026-08-15', 99.90, 'PENDING', now(), 0)
                """, occurrenceId, UUID.fromString(recurringId), UUID.fromString(envelopeId));

        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.overdue.length()").value(1))
                .andExpect(jsonPath("$.overdue[0].competence").value("2026-08"))
                .andExpect(jsonPath("$.current.length()").value(0));

        confirm(owner, occurrenceId.toString(), Map.of()).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/envelopes/{id}/entries", envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.items[0].occurredAt").value("2026-08-15"));
    }

    @Test
    @DisplayName("participante recebe 403, terceiro 404 e participante não vê pendências do dono")
    void authorization() throws Exception {
        String owner = verifiedToken("rec-auth-dono@example.com");
        String participant = verifiedToken("rec-auth-part@example.com");
        String stranger = token("rec-auth-estranho@example.com");
        String envelopeId = createEnvelope(owner, "Casa", "LIMIT", "3000.00");
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "rec-auth-part@example.com"))))
                .andExpect(status().isCreated());
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "1800.00", 15, "Aluguel"));
        generator.generateDue();
        String occurrenceId = firstPendingId(owner);

        mockMvc.perform(get(RECURRING, envelopeId).header("Authorization", "Bearer " + participant))
                .andExpect(status().isForbidden());
        createRecurring(participant, envelopeId, "EXPENSE", "10.00", 15, null).andExpect(status().isForbidden());
        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$.current.length()").value(0));
        confirm(participant, occurrenceId, Map.of()).andExpect(status().isForbidden());

        mockMvc.perform(get(RECURRING, envelopeId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        confirm(stranger, occurrenceId, Map.of()).andExpect(status().isNotFound());
        mockMvc.perform(delete(RECURRING + "/{rid}", envelopeId, recurringId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("dono cria recorrência de aporte numa meta")
    void ownerCreatesContribution() throws Exception {
        String owner = token("rec-aporte@example.com");
        String envelopeId = createEnvelope(owner, "Reserva", "GOAL", "500.00");

        createRecurring(owner, envelopeId, "CONTRIBUTION", "500.00", 10, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CONTRIBUTION"));
    }

    @Test
    @DisplayName("dia e valor inválidos são 400")
    void invalidInput() throws Exception {
        String owner = token("rec-invalid@example.com");
        String envelopeId = createEnvelope(owner, "Moradia", "LIMIT", "2000.00");

        createRecurring(owner, envelopeId, "EXPENSE", "10.00", 0, null).andExpect(status().isBadRequest());
        createRecurring(owner, envelopeId, "EXPENSE", "10.00", 32, null).andExpect(status().isBadRequest());
        createRecurring(owner, envelopeId, "EXPENSE", "0.00", 5, null).andExpect(status().isBadRequest());
        createRecurring(owner, envelopeId, "OTHER", "10.00", 5, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("editar mantém a sugestão da pendente; excluir mantém o confirmado e descarta a pendente")
    void editAndDelete() throws Exception {
        String owner = token("rec-edit@example.com");
        String envelopeId = createEnvelope(owner, "Moradia", "LIMIT", "5000.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "1800.00", 15, "Aluguel"));
        String confirmedRecurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "100.00", 15, "Condomínio"));
        generator.generateDue();

        mockMvc.perform(put(RECURRING + "/{rid}", envelopeId, recurringId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("kind", "EXPENSE", "amount", money("1900.00"),
                                "dayOfMonth", 15, "description", "Aluguel", "active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount.amount").value("1900.00"));
        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.current[?(@.description == 'Aluguel')].suggestedAmount.amount").value("1800.00"));

        String condoOccurrence = jdbc.queryForObject(
                "SELECT id FROM recurring_occurrence WHERE recurring_entry_id = ?", UUID.class,
                UUID.fromString(confirmedRecurringId)).toString();
        confirm(owner, condoOccurrence, Map.of()).andExpect(status().isOk());

        mockMvc.perform(delete(RECURRING + "/{rid}", envelopeId, confirmedRecurringId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(RECURRING + "/{rid}", envelopeId, recurringId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RECURRING, envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.current.length()").value(0));
        mockMvc.perform(get("/api/v1/envelopes/{id}/entries", envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Condomínio"));
    }

    @Test
    @DisplayName("desativar impede a geração; reativar depois do dia começa no mês seguinte")
    void deactivateAndReactivate() throws Exception {
        String owner = token("rec-toggle@example.com");
        String envelopeId = createEnvelope(owner, "Moradia", "LIMIT", "2000.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "1800.00", 15, null));
        Map<String, Object> body = new HashMap<>(Map.of("kind", "EXPENSE", "amount", money("1800.00"), "dayOfMonth", 15));

        body.put("active", false);
        update(owner, envelopeId, recurringId, body).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        generator.generateDue();
        assertThat(occurrenceCount(recurringId)).isZero();

        body.put("dayOfMonth", 10);
        body.put("active", true);
        update(owner, envelopeId, recurringId, body)
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.firstCompetence").value("2026-10"));
        generator.generateDue();
        assertThat(occurrenceCount(recurringId)).isZero();
    }

    @Test
    @DisplayName("arquivar a verba desativa recorrências e descarta pendentes")
    void archiveStopsRecurrences() throws Exception {
        String owner = token("rec-archive@example.com");
        String envelopeId = createEnvelope(owner, "Streaming", "LIMIT", "100.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "39.90", 15, null));
        generator.generateDue();

        mockMvc.perform(post("/api/v1/envelopes/{id}/archive", envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject("SELECT active FROM recurring_entry WHERE id = ?", Boolean.class,
                UUID.fromString(recurringId))).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM recurring_occurrence WHERE recurring_entry_id = ?",
                String.class, UUID.fromString(recurringId))).isEqualTo("DISCARDED");
        mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.current.length()").value(0));
        createRecurring(owner, envelopeId, "EXPENSE", "10.00", 15, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("exportação inclui recorrências; exclusão do titular as remove")
    void titularRights() throws Exception {
        String owner = token("rec-titular@example.com");
        String envelopeId = createEnvelope(owner, "Moradia", "LIMIT", "2000.00");
        String recurringId = idOf(createRecurring(owner, envelopeId, "EXPENSE", "1800.00", 15, "Aluguel"));
        generator.generateDue();

        mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurringEntries.length()").value(1))
                .andExpect(jsonPath("$.recurringEntries[0].amount").value("1800.00"))
                .andExpect(jsonPath("$.recurringEntries[0].description").value("Aluguel"))
                .andExpect(jsonPath("$.recurringOccurrences.length()").value(1))
                .andExpect(jsonPath("$.recurringOccurrences[0].status").value("PENDING"));

        mockMvc.perform(delete("/api/v1/privacy/account")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM recurring_entry WHERE id = ?", Integer.class,
                UUID.fromString(recurringId))).isZero();
        assertThat(occurrenceCount(recurringId)).isZero();
    }

    private ResultActions createRecurring(String token, String envelopeId, String kind, String amount, int day,
                                          String description) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("kind", kind, "amount", money(amount), "dayOfMonth", day));
        if (description != null) body.put("description", description);
        return mockMvc.perform(post(RECURRING, envelopeId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions update(String token, String envelopeId, String recurringId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(put(RECURRING + "/{rid}", envelopeId, recurringId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions confirm(String token, String occurrenceId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/recurring-occurrences/{id}/confirm", occurrenceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String idOf(ResultActions created) throws Exception {
        MvcResult result = created.andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String firstPendingId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get(PENDING).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("current").get(0).get("id").asString();
    }

    private void expectAvailable(String token, String amount) throws Exception {
        mockMvc.perform(get("/api/v1/ledger/summary").header("Authorization", "Bearer " + token).param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].available.amount").value(amount));
    }

    private int occurrenceCount(String recurringId) {
        return jdbc.queryForObject("SELECT count(*) FROM recurring_occurrence WHERE recurring_entry_id = ?",
                Integer.class, UUID.fromString(recurringId));
    }

    private String createEnvelope(String token, String name, String purpose, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", purpose, "baseAmount", money(baseAmount)))))
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

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }
}
