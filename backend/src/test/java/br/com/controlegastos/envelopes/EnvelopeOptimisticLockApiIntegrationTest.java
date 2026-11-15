package br.com.controlegastos.envelopes;

import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.HashMap;
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
class EnvelopeOptimisticLockApiIntegrationTest {

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
    void rejectsStaleVersionAndAcceptsCurrentVersionOnPatch() throws Exception {
        String token = tokenFor("lock-verba@example.com");
        MvcResult created = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Mercado",
                                "purpose", "LIMIT",
                                "baseAmount", money("100.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode envelope = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        String id = envelope.get("id").asString();
        long version = envelope.get("version").asLong();

        Map<String, Object> firstPatch = new HashMap<>();
        firstPatch.put("version", version);
        firstPatch.put("name", "Mercado atualizado");
        mockMvc.perform(patch("/api/v1/envelopes/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstPatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mercado atualizado"))
                .andExpect(jsonPath("$.version").value(version + 1));

        Map<String, Object> stalePatch = new HashMap<>();
        stalePatch.put("version", version);
        stalePatch.put("name", "Nome velho");
        mockMvc.perform(patch("/api/v1/envelopes/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stalePatch)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENVELOPE_CONCURRENT_CHANGE"));

        MvcResult current = mockMvc.perform(get("/api/v1/envelopes/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long currentVersion = objectMapper.readTree(current.getResponse().getContentAsByteArray()).get("version").asLong();

        Map<String, Object> freshPatch = new HashMap<>();
        freshPatch.put("version", currentVersion);
        freshPatch.put("name", "Mercado final");
        mockMvc.perform(patch("/api/v1/envelopes/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freshPatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mercado final"));
    }

    private String tokenFor(String email) throws Exception {
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

    private String newEnvelope(String token, String name, String base) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "purpose", "LIMIT", "baseAmount", money(base)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).get("id").asString();
    }

    private org.springframework.test.web.servlet.ResultActions patchEnvelope(String token, String id, Map<String, Object> body)
            throws Exception {
        var request = patch("/api/v1/envelopes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("edição sem versão é recusada com 400")
    void edicaoSemVersaoRecusada() throws Exception {
        String token = tokenFor("lock-sem-versao@example.com");
        String id = newEnvelope(token, "Mercado", "100.00");

        patchEnvelope(token, id, Map.of("name", "Outro")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("participante não edita a verba mesmo com a versão correta")
    void terceiroNaoEdita() throws Exception {
        String owner = tokenFor("lock-owner@example.com");
        String stranger = tokenFor("lock-stranger@example.com");
        String id = newEnvelope(owner, "Mercado", "100.00");

        int status = patchEnvelope(stranger, id, Map.of("version", 0, "name", "Invadida")).andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isIn(403, 404);
        mockMvc.perform(get("/api/v1/envelopes/{id}", id).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.name").value("Mercado"));
    }

    @Test
    @DisplayName("versão do futuro também é tratada como conflito")
    void versaoDoFuturoConflita() throws Exception {
        String token = tokenFor("lock-futuro@example.com");
        String id = newEnvelope(token, "Mercado", "100.00");

        patchEnvelope(token, id, Map.of("version", 99, "name", "Outro"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENVELOPE_CONCURRENT_CHANGE"));
    }

    @Test
    @DisplayName("editar verba inexistente responde 404")
    void editarInexistente() throws Exception {
        String token = tokenFor("lock-inexistente@example.com");

        patchEnvelope(token, java.util.UUID.randomUUID().toString(), Map.of("version", 0, "name", "X"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("nome com 81 caracteres é recusado com 400 e mantém a versão")
    void nomeLongoRecusado() throws Exception {
        String token = tokenFor("lock-nome@example.com");
        String id = newEnvelope(token, "Mercado", "100.00");

        patchEnvelope(token, id, Map.of("version", 0, "name", "x".repeat(81))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/envelopes/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("editar sem sessão responde 401")
    void editarSemSessao() throws Exception {
        patchEnvelope(null, java.util.UUID.randomUUID().toString(), Map.of("version", 0)).andExpect(status().isUnauthorized());
    }
}
