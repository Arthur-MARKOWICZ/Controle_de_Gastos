package br.com.controlegastos.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class EnvelopeEntrySoftDeleteApiIntegrationTest {

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
    void softDeletedExpenseDoesNotAppearInEnvelopeListing() throws Exception {
        String token = tokenFor("softdelete-list@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "200.00");
        String keepId = createExpense(token, envelopeId, "30.00");
        String deletedId = createExpense(token, envelopeId, "40.00");

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", deletedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(keepId));

        mockMvc.perform(get("/api/v1/ledger/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("170.00"));
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

    private Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private record Credentials(String email, String password) {}

    @Test
    @DisplayName("excluir duas vezes o mesmo gasto é idempotente")
    void excluirDuasVezesIdempotente() throws Exception {
        String token = tokenFor("softdelete-twice@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "200.00");
        String entryId = createExpense(token, envelopeId, "30.00");

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("terceiro sem vínculo não exclui o gasto: 403")
    void terceiroNaoExclui() throws Exception {
        String owner = tokenFor("softdelete-owner@example.com");
        String stranger = tokenFor("softdelete-stranger@example.com");
        String envelopeId = createEnvelope(owner, "Mercado", "LIMIT", "200.00");
        String entryId = createExpense(owner, envelopeId, "30.00");

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + owner).param("month", "2026-09"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("editar gasto excluído logicamente responde 404 LEDGER_ENTRY_NOT_FOUND")
    void editarExcluidoRecusado() throws Exception {
        String token = tokenFor("softdelete-edit@example.com");
        String envelopeId = createEnvelope(token, "Mercado", "LIMIT", "200.00");
        String entryId = createExpense(token, envelopeId, "30.00");
        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", entryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/ledger/entries/{id}", entryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("envelopeId", envelopeId, "amount", money("10.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ENTRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("excluir lançamento inexistente responde 404 LEDGER_ENTRY_NOT_FOUND")
    void excluirInexistenteResponde404() throws Exception {
        String token = tokenFor("softdelete-missing@example.com");

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ENTRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("identificador malformado responde 400 INVALID_REQUEST")
    void idMalformadoResponde400() throws Exception {
        String token = tokenFor("softdelete-bad-id@example.com");

        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", "nao-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("excluir sem sessão responde 401")
    void excluirSemSessao() throws Exception {
        mockMvc.perform(delete("/api/v1/ledger/entries/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
