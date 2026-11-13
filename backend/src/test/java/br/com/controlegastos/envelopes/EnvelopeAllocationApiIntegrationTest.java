package br.com.controlegastos.envelopes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class EnvelopeAllocationApiIntegrationTest {

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
    void rejectsEnvelopeCreateAndIncomeReductionWhenMonthlyAllocationExceedsIncome() throws Exception {
        String token = tokenFor("alocacao@example.com");
        putIncome(token, "1000.00");

        mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Mercado",
                                "purpose", "LIMIT",
                                "baseAmount", money("600.00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "IPVA",
                                "purpose", "ANNUAL_EXPENSE",
                                "annualAmount", money("1200.00"),
                                "dueMonth", 1,
                                "dueDay", 10,
                                "fundingMode", "MONTHLY"))))
                .andExpect(status().isCreated());

        // 600 base + 240 installment (Sep→Jan from fixed clock Sep 15) = 840; income 700 is below
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "700.00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOME_BELOW_BASE_ALLOCATIONS"));

        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "840.00"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Lazer",
                                "purpose", "LIMIT",
                                "baseAmount", money("10.00"))))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("allocation.exceeds_income"));
    }

    @Test
    void rejectsPatchThatWouldExceedIncome() throws Exception {
        String token = tokenFor("alocacao-patch@example.com");
        putIncome(token, "500.00");
        MvcResult created = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Mercado",
                                "purpose", "LIMIT",
                                "baseAmount", money("400.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        var envelope = objectMapper.readTree(created.getResponse().getContentAsByteArray());

        Map<String, Object> patch = new HashMap<>();
        patch.put("version", envelope.get("version").asLong());
        patch.put("baseAmount", money("600.00"));
        mockMvc.perform(patch("/api/v1/envelopes/{id}", envelope.get("id").asString())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("allocation.exceeds_income"));
    }

    private void putIncome(String token, String amount) throws Exception {
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", amount))))
                .andExpect(status().isOk());
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
    @DisplayName("alocar exatamente a renda é aceito")
    void alocarExatamenteARenda() throws Exception {
        String token = tokenFor("alloc-exato@example.com");
        putIncome(token, "1000.00");

        newEnvelope(token, "Aluguel", "1000.00");
    }

    @Test
    @DisplayName("verbas de outro titular não contam no meu limite de renda")
    void verbasDeOutroNaoContam() throws Exception {
        String me = tokenFor("alloc-me@example.com");
        String other = tokenFor("alloc-other@example.com");
        putIncome(me, "100.00");
        putIncome(other, "5000.00");
        newEnvelope(other, "Aluguel", "4000.00");

        newEnvelope(me, "Mercado", "100.00");
    }

    @Test
    @DisplayName("um centavo acima da renda é recusado com o problema de alocação")
    void umCentavoAcimaRecusado() throws Exception {
        String token = tokenFor("alloc-centavo@example.com");
        putIncome(token, "1000.00");
        newEnvelope(token, "Aluguel", "900.00");

        mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Lazer", "purpose", "LIMIT", "baseAmount", money("100.01")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A soma das verbas-base excede a renda mensal"));
    }

    @Test
    @DisplayName("verba-base negativa é recusada com 400")
    void verbaNegativaRecusada() throws Exception {
        String token = tokenFor("alloc-negativa@example.com");

        mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Lazer", "purpose", "LIMIT", "baseAmount", money("-1.00")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("verba arquivada libera a renda para outra verba")
    void arquivadaLiberaRenda() throws Exception {
        String token = tokenFor("alloc-arquivada@example.com");
        putIncome(token, "1000.00");
        String id = newEnvelope(token, "Aluguel", "1000.00");
        mockMvc.perform(post("/api/v1/envelopes/{id}/archive", id).header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());

        newEnvelope(token, "Novo aluguel", "1000.00");
    }

    @Test
    @DisplayName("criar verba sem sessão responde 401")
    void criarSemSessao() throws Exception {
        mockMvc.perform(post("/api/v1/envelopes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
