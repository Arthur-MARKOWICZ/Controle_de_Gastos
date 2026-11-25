package br.com.controlegastos.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Preferências de notificação e "lançou hoje" (spec notifications/notification-preferences).
 * O relógio de teste é 2026-09-15T15:00Z, ou seja, 12:00 de 2026-09-15 em São Paulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class NotificationPreferencesApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String PREFERENCES = "/api/v1/notifications/preferences";
    private static final String TODAY = "/api/v1/notifications/today";

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
    @DisplayName("conta nova tem os três tipos desligados e lembrete às 20:00")
    void defaults() throws Exception {
        String token = token("pref-padrao@example.com");

        mockMvc.perform(get(PREFERENCES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyReminder").value(false))
                .andExpect(jsonPath("$.negativeBalance").value(false))
                .andExpect(jsonPath("$.sharedExpense").value(false))
                .andExpect(jsonPath("$.reminderTime").value("20:00"));
    }

    @Test
    @DisplayName("ativar um tipo não muda os demais e o horário é guardado")
    void updatesOnlyWhatChanged() throws Exception {
        String token = token("pref-ativa@example.com");

        save(token, false, true, false, "21:30").andExpect(status().isOk())
                .andExpect(jsonPath("$.negativeBalance").value(true));

        mockMvc.perform(get(PREFERENCES).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.dailyReminder").value(false))
                .andExpect(jsonPath("$.negativeBalance").value(true))
                .andExpect(jsonPath("$.sharedExpense").value(false))
                .andExpect(jsonPath("$.reminderTime").value("21:30"));
    }

    @Test
    @DisplayName("horário fora do passo de 15 minutos ou mal formado é recusado e mantém o anterior")
    void rejectsInvalidTime() throws Exception {
        String token = token("pref-horario@example.com");
        save(token, true, false, false, "21:00").andExpect(status().isOk());

        save(token, true, false, false, "21:10").andExpect(status().isBadRequest());
        save(token, true, false, false, "25:00").andExpect(status().isBadRequest());
        save(token, true, false, false, "nove").andExpect(status().isBadRequest());

        mockMvc.perform(get(PREFERENCES).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.reminderTime").value("21:00"));
    }

    @Test
    @DisplayName("exige sessão e cada conta só enxerga as próprias preferências")
    void privateToTheAccount() throws Exception {
        mockMvc.perform(get(PREFERENCES)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(TODAY)).andExpect(status().isUnauthorized());

        String owner = token("pref-dono@example.com");
        String other = token("pref-outro@example.com");
        save(owner, true, true, true, "06:45").andExpect(status().isOk());

        mockMvc.perform(get(PREFERENCES).header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.dailyReminder").value(false))
                .andExpect(jsonPath("$.reminderTime").value("20:00"));
    }

    @Test
    @DisplayName("lançou hoje: false sem gasto, true com gasto, false se o único foi excluído")
    void todayReflectsOwnExpenses() throws Exception {
        String token = token("pref-hoje@example.com");
        String envelopeId = createEnvelope(token);

        mockMvc.perform(get(TODAY).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-09-15"))
                .andExpect(jsonPath("$.hasExpense").value(false));

        String entryId = expense(token, envelopeId, "2026-09-15");
        expense(token, envelopeId, "2026-09-14");
        String body = mockMvc.perform(get(TODAY).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasExpense").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("12.50").doesNotContain(envelopeId);

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId).header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(TODAY).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasExpense").value(false));
    }

    @Test
    @DisplayName("gasto do participante na verba do dono não conta para o dono")
    void participantExpenseDoesNotCountForOwner() throws Exception {
        String owner = verifiedToken("pref-hoje-dono@example.com");
        String participant = verifiedToken("pref-hoje-part@example.com");
        String envelopeId = createEnvelope(owner);
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "pref-hoje-part@example.com"))))
                .andExpect(status().isCreated());

        expense(participant, envelopeId, "2026-09-15");

        mockMvc.perform(get(TODAY).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.hasExpense").value(false));
        mockMvc.perform(get(TODAY).header("Authorization", "Bearer " + participant))
                .andExpect(jsonPath("$.hasExpense").value(true));
    }

    @Test
    @DisplayName("preferências entram na exportação e somem com a exclusão da conta")
    void exportAndErasure() throws Exception {
        String token = token("pref-titular@example.com");
        save(token, true, false, false, "21:00").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationPreferences.dailyReminder").value(true))
                .andExpect(jsonPath("$.notificationPreferences.negativeBalance").value(false))
                .andExpect(jsonPath("$.notificationPreferences.reminderTime").value("21:00"));

        mockMvc.perform(delete("/api/v1/privacy/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_preference p
                JOIN user_account u ON u.id = p.user_id
                WHERE u.email_normalized = ?
                """, Integer.class, "pref-titular@example.com")).isZero();
    }

    private ResultActions save(String token, boolean daily, boolean negative, boolean shared, String time)
            throws Exception {
        return mockMvc.perform(put(PREFERENCES)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "dailyReminder", daily, "negativeBalance", negative,
                        "sharedExpense", shared, "reminderTime", time))));
    }

    private String expense(String token, String envelopeId, String date) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "EXPENSE", "amount", Map.of("amount", "12.50", "currency", "BRL"),
                                "occurredAt", date))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String createEnvelope(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Mercado", "purpose", "LIMIT",
                                "baseAmount", Map.of("amount", "500.00", "currency", "BRL")))))
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
}
