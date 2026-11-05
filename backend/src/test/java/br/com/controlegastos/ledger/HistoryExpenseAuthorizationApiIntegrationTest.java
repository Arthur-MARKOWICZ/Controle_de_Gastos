package br.com.controlegastos.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class HistoryExpenseAuthorizationApiIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void participantAuthorCanEditAndDeleteOwnExpenseWhileStrangerIsForbidden() throws Exception {
        String ownerToken = tokenFor("authz-owner@example.com");
        UUID ownerId = userId("authz-owner@example.com");
        String participantToken = tokenFor("authz-participant@example.com");
        UUID participantId = userId("authz-participant@example.com");
        String strangerToken = tokenFor("authz-stranger@example.com");

        String envelopeId = createEnvelope(ownerToken, "Mercado", "LIMIT", "200.00");
        jdbc.update(
                "INSERT INTO envelope_participant(envelope_id, user_id, added_at, added_by) VALUES (?, ?, ?, ?)",
                UUID.fromString(envelopeId), participantId, Timestamp.from(Instant.now()), ownerId);

        String entryId = createExpense(participantToken, envelopeId, "40.00");

        mockMvc.perform(patch("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "envelopeId", envelopeId,
                                "amount", money("35.00")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + participantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "envelopeId", envelopeId,
                                "amount", money("35.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount.amount").value("35.00"));

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + participantToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void ownerCanEditExpenseAuthoredByParticipant() throws Exception {
        String ownerToken = tokenFor("authz-owner-edit@example.com");
        UUID ownerId = userId("authz-owner-edit@example.com");
        String participantToken = tokenFor("authz-participant-edit@example.com");
        UUID participantId = userId("authz-participant-edit@example.com");

        String envelopeId = createEnvelope(ownerToken, "Lazer", "LIMIT", "300.00");
        jdbc.update(
                "INSERT INTO envelope_participant(envelope_id, user_id, added_at, added_by) VALUES (?, ?, ?, ?)",
                UUID.fromString(envelopeId), participantId, Timestamp.from(Instant.now()), ownerId);

        String entryId = createExpense(participantToken, envelopeId, "50.00");

        mockMvc.perform(patch("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "envelopeId", envelopeId,
                                "amount", money("45.00"),
                                "description", "Ajuste do titular"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount.amount").value("45.00"))
                .andExpect(jsonPath("$.description").value("Ajuste do titular"));
    }

    @Test
    void participantAuthorCannotMoveExpenseToOwnEnvelope() throws Exception {
        String ownerToken = tokenFor("authz-owner-steal@example.com");
        UUID ownerId = userId("authz-owner-steal@example.com");
        String participantToken = tokenFor("authz-participant-steal@example.com");
        UUID participantId = userId("authz-participant-steal@example.com");

        String ownerEnvelopeId = createEnvelope(ownerToken, "Mercado", "LIMIT", "200.00");
        jdbc.update(
                "INSERT INTO envelope_participant(envelope_id, user_id, added_at, added_by) VALUES (?, ?, ?, ?)",
                UUID.fromString(ownerEnvelopeId), participantId, Timestamp.from(Instant.now()), ownerId);

        String participantEnvelopeId = createEnvelope(participantToken, "Pessoal", "LIMIT", "150.00");
        String entryId = createExpense(participantToken, ownerEnvelopeId, "40.00");

        mockMvc.perform(patch("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + participantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "envelopeId", participantEnvelopeId,
                                "amount", money("40.00")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanMoveExpenseBetweenOwnVisibleEnvelopes() throws Exception {
        String ownerToken = tokenFor("authz-owner-move@example.com");

        String sourceEnvelopeId = createEnvelope(ownerToken, "Mercado", "LIMIT", "200.00");
        String destinationEnvelopeId = createEnvelope(ownerToken, "Lazer", "LIMIT", "150.00");
        String entryId = createExpense(ownerToken, sourceEnvelopeId, "40.00");

        mockMvc.perform(patch("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "envelopeId", destinationEnvelopeId,
                                "amount", money("40.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopeId").value(destinationEnvelopeId));
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

    private String createExpense(String token, String envelopeId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "EXPENSE",
                                "amount", money(amount),
                                "occurredAt", "2026-09-10"))))
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
        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return response.get("accessToken").asString();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
    }

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private record Credentials(String email, String password) {}
}
