package br.com.controlegastos.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.income.application.IncomeService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class IncomeApiIntegrationTest {

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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthAttemptRepository authAttempts;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void incomeEndpointsRequireTheExistingAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/income"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void recordsExactDecimalIncomeAndReturnsEffectiveValueAndPaginatedHistory() throws Exception {
        String token = tokenFor("renda@example.com");
        YearMonth current = YearMonth.now(IncomeService.BUSINESS_ZONE);
        String currentMonth = current.toString();

        putIncome(token, "5000.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("5000.00"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.effectiveFrom").value(currentMonth));
        putIncome(token, "5000.0").andExpect(status().isOk());
        putIncome(token, "6200.10").andExpect(status().isOk());

        UUID ownerId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE email_normalized = ?",
                UUID.class,
                "renda@example.com");
        YearMonth previous = current.minusMonths(1);
        jdbc.update(
                "INSERT INTO monthly_income(owner_id, effective_month, amount, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, 0)",
                ownerId, previous.atDay(1), new BigDecimal("4100.25"), Timestamp.from(java.time.Instant.now()));

        mockMvc.perform(get("/api/v1/income?month=" + previous)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("4100.25"))
                .andExpect(jsonPath("$.effectiveFrom").value(previous.toString()));

        mockMvc.perform(get("/api/v1/income")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("6200.10"));

        mockMvc.perform(get("/api/v1/income/history?page=0&size=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amount").value("6200.10"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));

        BigDecimal persisted = jdbc.queryForObject(
                "SELECT amount FROM monthly_income WHERE owner_id = ? AND effective_month = ?",
                BigDecimal.class,
                ownerId,
                current.atDay(1));
        assertThat(persisted).isEqualByComparingTo("6200.10");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM income_revision WHERE owner_id = ?", Integer.class, ownerId)).isEqualTo(2);
    }

    @Test
    void rejectsNumbersInsteadOfDecimalStringsRoundingNegativeValuesAndInvalidPagination() throws Exception {
        String token = tokenFor("validacao-renda@example.com");

        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(put("/api/v1/income")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"5000.00\",\"ownerId\":\"00000000-0000-0000-0000-000000000000\"}"))
                .andExpect(status().isBadRequest());
        putIncome(token, "10.999").andExpect(status().isBadRequest());
        putIncome(token, "1E+3").andExpect(status().isBadRequest());
        putIncome(token, "-0.01").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/income/history?size=101")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/income/history?page=not-a-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doesNotExposeAnotherUsersIncomeAndDistinguishesMissingFromZero() throws Exception {
        String ownerToken = tokenFor("titular-renda@example.com");
        putIncome(ownerToken, "0.00").andExpect(status().isOk());

        String otherToken = tokenFor("outra-renda@example.com");
        mockMvc.perform(get("/api/v1/income")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INCOME_NOT_CONFIGURED"));

        mockMvc.perform(get("/api/v1/income")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("0.00"));
    }

    private org.springframework.test.web.servlet.ResultActions putIncome(String token, String amount)
            throws Exception {
        return mockMvc.perform(put("/api/v1/income")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"" + amount + "\"}"));
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(new Credentials(email, PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        return response.get("accessToken").asString();
    }

    private record Credentials(String email, String password) {
    }
}
