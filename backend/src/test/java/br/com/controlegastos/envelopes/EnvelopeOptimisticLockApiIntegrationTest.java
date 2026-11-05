package br.com.controlegastos.envelopes;

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
}
