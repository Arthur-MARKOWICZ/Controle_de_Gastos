package br.com.controlegastos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Varredura da regra do AGENTS.md/ADR-0030: todo endpoint autenticado recusa
 * requisição sem sessão (401) e todo endpoint de recurso recusa terceiro sem
 * vínculo (403/404) sem expor dados do recurso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class EndpointAuthorizationApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String SECRET_NAME = "Verba Secreta do Titular";
    private static final String ANY = "00000000-0000-0000-0000-000000000001";

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

    static Stream<Arguments> authenticatedEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/users/me"),
                Arguments.of("GET", "/api/v1/auth/login-methods"),
                Arguments.of("POST", "/api/v1/auth/logout"),
                Arguments.of("POST", "/api/v1/auth/password"),
                Arguments.of("DELETE", "/api/v1/auth/oauth/google"),
                Arguments.of("GET", "/api/v1/mfa/status"),
                Arguments.of("POST", "/api/v1/mfa/enroll"),
                Arguments.of("POST", "/api/v1/mfa/enroll/confirm"),
                Arguments.of("POST", "/api/v1/mfa/disable"),
                Arguments.of("POST", "/api/v1/mfa/recovery-codes"),
                Arguments.of("GET", "/api/v1/income"),
                Arguments.of("PUT", "/api/v1/income"),
                Arguments.of("GET", "/api/v1/income/history"),
                Arguments.of("GET", "/api/v1/envelopes"),
                Arguments.of("POST", "/api/v1/envelopes"),
                Arguments.of("GET", "/api/v1/envelopes/" + ANY),
                Arguments.of("PATCH", "/api/v1/envelopes/" + ANY),
                Arguments.of("POST", "/api/v1/envelopes/" + ANY + "/archive"),
                Arguments.of("GET", "/api/v1/envelopes/" + ANY + "/entries"),
                Arguments.of("POST", "/api/v1/envelopes/" + ANY + "/entries"),
                Arguments.of("GET", "/api/v1/envelopes/" + ANY + "/participants"),
                Arguments.of("POST", "/api/v1/envelopes/" + ANY + "/participants"),
                Arguments.of("DELETE", "/api/v1/envelopes/" + ANY + "/participants/" + ANY),
                Arguments.of("PATCH", "/api/v1/ledger/entries/" + ANY),
                Arguments.of("DELETE", "/api/v1/ledger/entries/" + ANY),
                Arguments.of("GET", "/api/v1/ledger/entries/" + ANY + "/revisions"),
                Arguments.of("GET", "/api/v1/ledger/summary"),
                Arguments.of("GET", "/api/v1/history?from=2026-09-01&to=2026-09-30"),
                Arguments.of("GET", "/api/v1/history/summary?from=2026-09-01&to=2026-09-30"),
                Arguments.of("GET", "/api/v1/reports/expenses-by-purpose?from=2026-09-01&to=2026-09-30&format=csv"),
                Arguments.of("GET", "/api/v1/reports/limit-exceeded-months?from=2026-09-01&to=2026-09-30&format=csv"),
                Arguments.of("GET", "/api/v1/reports/goals-below-target?from=2026-09-01&to=2026-09-30&format=csv"),
                Arguments.of("GET", "/api/v1/alerts"),
                Arguments.of("POST", "/api/v1/alerts/" + ANY + "/dismiss"),
                Arguments.of("GET", "/api/v1/privacy/export"),
                Arguments.of("DELETE", "/api/v1/privacy/account"));
    }

    @ParameterizedTest(name = "{0} {1} sem sessão responde 401")
    @MethodSource("authenticatedEndpoints")
    @DisplayName("endpoint autenticado sem sessão responde 401")
    void semSessaoResponde401(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} com token inválido responde 401")
    @MethodSource("authenticatedEndpoints")
    @DisplayName("endpoint autenticado com token forjado responde 401")
    void tokenForjadoResponde401(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.assinatura-falsa")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> resourceEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/envelopes/{envelope}", "{}"),
                Arguments.of("PATCH", "/api/v1/envelopes/{envelope}", "{\"version\":0,\"name\":\"Invadida\"}"),
                Arguments.of("POST", "/api/v1/envelopes/{envelope}/archive", "{}"),
                Arguments.of("GET", "/api/v1/envelopes/{envelope}/entries?month=2026-09", "{}"),
                Arguments.of("POST", "/api/v1/envelopes/{envelope}/entries",
                        "{\"kind\":\"EXPENSE\",\"amount\":{\"amount\":\"1.00\",\"currency\":\"BRL\"},\"occurredAt\":\"2026-09-10\"}"),
                Arguments.of("GET", "/api/v1/envelopes/{envelope}/participants", "{}"),
                Arguments.of("POST", "/api/v1/envelopes/{envelope}/participants", "{\"email\":\"x@example.com\"}"),
                Arguments.of("PATCH", "/api/v1/ledger/entries/{entry}",
                        "{\"envelopeId\":\"{envelope}\",\"amount\":{\"amount\":\"1.00\",\"currency\":\"BRL\"}}"),
                Arguments.of("DELETE", "/api/v1/ledger/entries/{entry}", "{}"),
                Arguments.of("GET", "/api/v1/ledger/entries/{entry}/revisions", "{}"));
    }

    @ParameterizedTest(name = "terceiro em {0} {1} responde 403/404")
    @MethodSource("resourceEndpoints")
    @DisplayName("terceiro sem vínculo não acessa recurso de outro titular")
    void terceiroNaoAcessa(String method, String template, String bodyTemplate) throws Exception {
        String owner = tokenFor("authz-owner-" + UUID.randomUUID() + "@example.com");
        String stranger = tokenFor("authz-stranger-" + UUID.randomUUID() + "@example.com");
        String envelopeId = createEnvelope(owner);
        String entryId = createExpense(owner, envelopeId);
        String path = template.replace("{envelope}", envelopeId).replace("{entry}", entryId);
        String body = bodyTemplate.replace("{envelope}", envelopeId);

        MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isIn(403, 404);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET_NAME);
    }

    private String createEnvelope(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", SECRET_NAME, "purpose", "LIMIT",
                                "baseAmount", Map.of("amount", "100.00", "currency", "BRL")))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String createExpense(String token, String envelopeId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("kind", "EXPENSE",
                                "amount", Map.of("amount", "10.00", "currency", "BRL"), "occurredAt", "2026-09-10"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }
}
