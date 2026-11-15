package br.com.controlegastos.envelopes;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.junit.jupiter.api.DisplayName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class AnnualDueDateValidationApiIntegrationTest {

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
    void rejectsInvalidCalendarDueDateForAnnualExpense() throws Exception {
        String token = tokenFor("due-invalido@example.com");
        putIncome(token, "2000.00");

        mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "IPVA",
                                "purpose", "ANNUAL_EXPENSE",
                                "annualAmount", money("1200.00"),
                                "dueMonth", 2,
                                "dueDay", 31,
                                "fundingMode", "MONTHLY"))))
                .andExpect(status().isBadRequest());
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

    private org.springframework.test.web.servlet.ResultActions annual(String token, int month, int day) throws Exception {
        return mockMvc.perform(post("/api/v1/envelopes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "IPVA", "purpose", "ANNUAL_EXPENSE",
                        "annualAmount", money("1200.00"), "dueMonth", month, "dueDay", day, "fundingMode", "MONTHLY"))));
    }

    @Test
    @DisplayName("29 de fevereiro é aceito como vencimento anual")
    void vinteNoveFevereiroAceito() throws Exception {
        String token = tokenFor("due-29-02@example.com");
        putIncome(token, "2000.00");

        annual(token, 2, 29).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("31 de dezembro é aceito como vencimento anual")
    void trintaUmDezembroAceito() throws Exception {
        String token = tokenFor("due-31-12@example.com");
        putIncome(token, "2000.00");

        annual(token, 12, 31).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("mês 13 é recusado com 400")
    void mesTrezeRecusado() throws Exception {
        String token = tokenFor("due-13@example.com");

        annual(token, 13, 1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("dia zero é recusado com 400")
    void diaZeroRecusado() throws Exception {
        String token = tokenFor("due-zero@example.com");

        annual(token, 1, 0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("31 de abril é recusado com 400")
    void trintaUmAbrilRecusado() throws Exception {
        String token = tokenFor("due-31-04@example.com");

        annual(token, 4, 31).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("criar gasto anual sem sessão responde 401")
    void semSessao() throws Exception {
        mockMvc.perform(post("/api/v1/envelopes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
