package br.com.controlegastos.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
class ReportingApiIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthAttemptRepository authAttempts;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    void reportEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/reports/expenses-by-purpose?from=2026-01-01&to=2026-01-31&format=csv"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void exportsActiveExpensesAsCsvAndXlsxWithoutFormulaInjection() throws Exception {
        String token = tokenFor("relatorio-titular@example.com");
        UUID owner = userId("relatorio-titular@example.com");
        UUID envelope = insertEnvelope(owner, "=Mercado", "LIMIT", "100.00");
        insertEntry(envelope, owner, "EXPENSE", "120.00", LocalDate.of(2026, 1, 15), null);
        insertEntry(envelope, owner, "EXPENSE", "10.00", LocalDate.of(2026, 1, 16), Instant.now());
        UUID deletedOnly = insertEnvelope(owner, "Excluída", "LIMIT", "100.00");
        insertEntry(deletedOnly, owner, "EXPENSE", "150.00", LocalDate.of(2026, 1, 17), Instant.now());

        MvcResult csv = mockMvc.perform(get("/api/v1/reports/expenses-by-purpose?from=2026-01-01&to=2026-01-31&format=csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        String csvText = new String(csv.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvText).contains("'=Mercado").contains("120,00").doesNotContain("10,00");

        MvcResult xlsxStart = mockMvc.perform(get("/api/v1/reports/expenses-by-purpose?from=2026-01-01&to=2026-01-31&format=xlsx")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        MvcResult xlsx = mockMvc.perform(asyncDispatch(xlsxStart))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        try (var workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("'=Mercado");
        }

        MvcResult limits = mockMvc.perform(get("/api/v1/reports/limit-exceeded-months?from=2026-01-01&to=2026-01-31&format=csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertThat(new String(limits.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("'=Mercado").doesNotContain("Excluída");
    }

    @Test
    void letsParticipantsExportVisibleEnvelopesAndRejectsPartialMonthlyRanges() throws Exception {
        String ownerToken = tokenFor("relatorio-owner@example.com");
        UUID owner = userId("relatorio-owner@example.com");
        String participantToken = tokenFor("relatorio-participante@example.com");
        UUID participant = userId("relatorio-participante@example.com");
        UUID envelope = insertEnvelope(owner, "Viagem", "GOAL", "80.00");
        jdbc.update("INSERT INTO envelope_participant(envelope_id, user_id, added_at, added_by) VALUES (?, ?, ?, ?)",
                envelope, participant, Timestamp.from(Instant.now()), owner);
        insertEntry(envelope, owner, "CONTRIBUTION", "30.00", LocalDate.of(2026, 1, 15), null);

        MvcResult report = mockMvc.perform(get("/api/v1/reports/goals-below-target?from=2026-01-01&to=2026-01-31&format=csv")
                        .header("Authorization", "Bearer " + participantToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(new String(report.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("Viagem;1;80,00;30,00;50,00");

        mockMvc.perform(get("/api/v1/reports/limit-exceeded-months?from=2026-01-02&to=2026-01-31&format=csv")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private UUID insertEnvelope(UUID owner, String name, String purpose, String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO envelope(id, owner_id, name, purpose, base_amount, created_at, version) VALUES (?, ?, ?, ?, ?, ?, 0)",
                id, owner, name, purpose, new BigDecimal(amount), Timestamp.from(Instant.parse("2026-01-02T12:00:00Z")));
        return id;
    }

    private void insertEntry(UUID envelope, UUID owner, String kind, String amount, LocalDate occurredAt, Instant deletedAt) {
        jdbc.update("INSERT INTO ledger_entry(id, envelope_id, owner_id, author_id, amount, kind, occurred_at, created_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), envelope, owner, owner, new BigDecimal(amount), kind, occurredAt,
                Timestamp.from(Instant.now()), deletedAt == null ? null : Timestamp.from(deletedAt));
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
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

    private record Credentials(String email, String password) {
    }
}
