package br.com.controlegastos.ledger;

import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class SavingsTargetApiIntegrationTest {

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
    void keepsSavingsTargetAcrossContributionsAndAnnouncesOnlyItsFirstCrossing() throws Exception {
        String token = tokenFor("meta-acumulacao@example.com");
        String envelopeId = createSavingsTarget(token);

        register(token, envelopeId, "100.00")
                .andExpect(jsonPath("$.targetJustReached").value(false));
        register(token, envelopeId, "900.00")
                .andExpect(jsonPath("$.targetJustReached").value(true));
        register(token, envelopeId, "50.00")
                .andExpect(jsonPath("$.targetJustReached").value(false));

        mockMvc.perform(get("/api/v1/ledger/summary?month=2026-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].targetAmount.amount").value("1000.00"))
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1050.00"))
                .andExpect(jsonPath("$.envelopes[0].targetReachedAt").isNotEmpty());
    }

    private String createSavingsTarget(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Notebook", "purpose", "SAVINGS_TARGET",
                "baseAmount", money("0.00"), "targetAmount", money("1000.00")));
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetAmount.amount").value("1000.00"))
                .andExpect(jsonPath("$.targetReachedAt").doesNotExist())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private org.springframework.test.web.servlet.ResultActions register(String token, String envelopeId, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "CONTRIBUTION", "amount", money(amount), "occurredAt", "2026-09-01"))))
                .andExpect(status().isCreated());
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

    private record Credentials(String email, String password) {
    }

    private org.springframework.test.web.servlet.ResultActions createEnvelopeRaw(String token, Object body) throws Exception {
        return mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("meta de acumulação sem valor-alvo é recusada com 400")
    void semValorAlvoRecusada() throws Exception {
        String token = tokenFor("target-missing@example.com");

        createEnvelopeRaw(token, Map.of("name", "Notebook", "purpose", "SAVINGS_TARGET", "baseAmount", money("0.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("valor-alvo zero é recusado com 400")
    void valorAlvoZeroRecusado() throws Exception {
        String token = tokenFor("target-zero@example.com");

        createEnvelopeRaw(token, Map.of("name", "Notebook", "purpose", "SAVINGS_TARGET",
                "baseAmount", money("0.00"), "targetAmount", money("0.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("meta de acumulação com valor-base mensal é recusada com 400")
    void comValorBaseRecusada() throws Exception {
        String token = tokenFor("target-base@example.com");

        createEnvelopeRaw(token, Map.of("name", "Notebook", "purpose", "SAVINGS_TARGET",
                "baseAmount", money("10.00"), "targetAmount", money("100.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("terceiro não aporta na meta de acumulação de outro titular")
    void terceiroNaoAporta() throws Exception {
        String owner = tokenFor("target-owner@example.com");
        String stranger = tokenFor("target-stranger@example.com");
        String envelopeId = createSavingsTarget(owner);

        int status = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "CONTRIBUTION", "amount", money("1000.00"), "occurredAt", "2026-09-01"))))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isIn(403, 404);
        mockMvc.perform(get("/api/v1/ledger/summary?month=2026-09").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.envelopes[0].targetReachedAt").doesNotExist());
    }

    @Test
    @DisplayName("criar meta sem sessão responde 401")
    void criarSemSessao() throws Exception {
        mockMvc.perform(post("/api/v1/envelopes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
