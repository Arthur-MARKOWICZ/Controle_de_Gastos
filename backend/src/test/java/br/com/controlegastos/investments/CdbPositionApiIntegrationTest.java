package br.com.controlegastos.investments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Contrato HTTP das posições de CDB prefixado (spec investments/cdb-positions, ADR-0034).
 * Relógio fixo: 2026-11-26 em America/Sao_Paulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CdbPositionApiIntegrationTest.CdbFixedClock.class)
class CdbPositionApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String CDBS = "/api/v1/investments/cdbs";

    @TestConfiguration(proxyBeanMethods = false)
    static class CdbFixedClock {
        /** 2026-11-27 02:00 UTC = 2026-11-26 23:00 America/Sao_Paulo. */
        static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

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
    @DisplayName("titular cadastra um CDB e recebe o estimado de um ano de rendimento")
    void registersCdbWithEstimatedValue() throws Exception {
        String titular = token("cdb-cria@example.com");

        create(titular, cdb("10000.00", "0.1250", "2025-11-26", "2027-11-26"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.principal.amount").value("10000.00"))
                .andExpect(jsonPath("$.principal.currency").value("BRL"))
                .andExpect(jsonPath("$.annualRate").value("0.1250"))
                .andExpect(jsonPath("$.startDate").value("2025-11-26"))
                .andExpect(jsonPath("$.maturityDate").value("2027-11-26"))
                .andExpect(jsonPath("$.estimatedValue.amount").value("11250.00"))
                .andExpect(jsonPath("$.estimatedValue.currency").value("BRL"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("segundo CDB cria outra posição")
    void secondCdbCreatesAnotherPosition() throws Exception {
        String titular = token("cdb-repete@example.com");
        String first = idOf(create(titular, cdb("1000.00", "0.1000", "2026-01-01", "2027-01-01")));
        String second = idOf(create(titular, cdb("2000.00", "0.1100", "2026-02-01", "2027-02-01")));

        assertThat(first).isNotEqualTo(second);
        list(titular).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("taxa, principal e datas inválidos respondem 400 sem criar a posição")
    void invalidInputIsRejected() throws Exception {
        String titular = token("cdb-invalido@example.com");

        for (String rate : new String[] {"12.50", "0.125", "0.12500", "0.0000", "-0.0100"}) {
            create(titular, cdb("10000.00", rate, "2025-11-26", "2027-11-26"))
                    .andExpect(status().isBadRequest());
        }
        for (String principal : new String[] {"0.00", "-1.00", "1000.001"}) {
            create(titular, cdb(principal, "0.1250", "2025-11-26", "2027-11-26"))
                    .andExpect(status().isBadRequest());
        }
        create(titular, cdb("10000.00", "0.1250", "2026-11-27", "2027-11-26"))
                .andExpect(status().isBadRequest());
        create(titular, cdb("10000.00", "0.1250", "2025-01-01", "2025-01-01"))
                .andExpect(status().isBadRequest());
        create(titular, cdb("10000.00", "0.1250", "2025-01-01", "2024-12-31"))
                .andExpect(status().isBadRequest());
        createRaw(titular, "{\"principal\":{\"amount\":\"10000.00\",\"currency\":\"BRL\"}}")
                .andExpect(status().isBadRequest());

        list(titular).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("início hoje é aceito e o estimado iguala o principal")
    void startTodayIsAccepted() throws Exception {
        String titular = token("cdb-hoje@example.com");

        create(titular, cdb("5000.00", "0.1250", "2026-11-26", "2027-11-26"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedValue.amount").value("5000.00"));
    }

    @Test
    @DisplayName("listagem ordena por data de início; sem posições é vazia; estimado congela no vencimento")
    void listingOrderEmptyAndFreezeAtMaturity() throws Exception {
        String titular = token("cdb-ordem@example.com");
        list(titular).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        create(titular, cdb("1000.00", "0.1000", "2026-10-01", "2027-10-01"));
        create(titular, cdb("2000.00", "0.1000", "2026-09-15", "2027-09-15"));
        create(titular, cdb("10000.00", "0.1000", "2024-11-26", "2025-11-26"));

        list(titular).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startDate").value("2024-11-26"))
                .andExpect(jsonPath("$[0].estimatedValue.amount").value("11000.00"))
                .andExpect(jsonPath("$[1].startDate").value("2026-09-15"))
                .andExpect(jsonPath("$[2].startDate").value("2026-10-01"));
    }

    @Test
    @DisplayName("titular exclui a própria posição; id desconhecido responde 404")
    void deletesOwnPosition() throws Exception {
        String titular = token("cdb-exclui@example.com");
        String id = idOf(create(titular, cdb("1000.00", "0.1000", "2026-01-01", "2027-01-01")));

        mockMvc.perform(delete(CDBS + "/{id}", id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNoContent());
        list(titular).andExpect(jsonPath("$.length()").value(0));
        assertThat(positionCount(id)).isZero();

        mockMvc.perform(delete(CDBS + "/{id}", id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CDB_POSITION_NOT_FOUND"));
    }

    @Test
    @DisplayName("outro usuário e participante de verba não veem nem excluem o CDB; sem token é 401")
    void onlyTitularReachesCdbs() throws Exception {
        String titular = verifiedToken("cdb-auth-dono@example.com");
        String participant = verifiedToken("cdb-auth-part@example.com");
        String stranger = token("cdb-auth-estranho@example.com");
        String envelopeId = createEnvelope(titular, "Casa", "3000.00");
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "cdb-auth-part@example.com"))))
                .andExpect(status().isCreated());
        String id = idOf(create(titular, cdb("1000.00", "0.1000", "2026-01-01", "2027-01-01")));

        for (String other : new String[] {participant, stranger}) {
            list(other).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            mockMvc.perform(delete(CDBS + "/{id}", id).header("Authorization", "Bearer " + other))
                    .andExpect(status().isNotFound());
        }
        assertThat(positionCount(id)).isEqualTo(1);

        mockMvc.perform(get(CDBS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(CDBS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                cdb("1000.00", "0.1000", "2026-01-01", "2027-01-01"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(CDBS + "/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cadastrar e excluir CDB não cria lançamento nem muda saldo de verba")
    void registeringCdbDoesNotTouchTheBudget() throws Exception {
        String titular = token("cdb-orcamento@example.com");
        createEnvelope(titular, "Investir", "1000.00");

        String id = idOf(create(titular, cdb("5000.00", "0.1250", "2025-11-26", "2027-11-26")));
        mockMvc.perform(delete(CDBS + "/{id}", id).header("Authorization", "Bearer " + titular))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ledger_entry e JOIN user_account u ON u.id = e.owner_id WHERE u.email_normalized = ?",
                Integer.class, "cdb-orcamento@example.com")).isZero();
        mockMvc.perform(get("/api/v1/ledger/summary").header("Authorization", "Bearer " + titular)
                        .param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelopes[0].available.amount").value("1000.00"));
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return createRaw(token, objectMapper.writeValueAsString(body));
    }

    private ResultActions createRaw(String token, String body) throws Exception {
        return mockMvc.perform(post(CDBS).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(String token) throws Exception {
        return mockMvc.perform(get(CDBS).header("Authorization", "Bearer " + token));
    }

    private static Map<String, Object> cdb(String principal, String rate, String start, String maturity) {
        return Map.of(
                "principal", Map.of("amount", principal, "currency", "BRL"),
                "annualRate", rate,
                "startDate", start,
                "maturityDate", maturity);
    }

    private String idOf(ResultActions result) throws Exception {
        MvcResult created = result.andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private int positionCount(String id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM cdb_position WHERE id = ?", Integer.class, UUID.fromString(id));
    }

    private String createEnvelope(String token, String name, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", "LIMIT",
                                "baseAmount", Map.of("amount", baseAmount, "currency", "BRL")))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String verifiedToken(String email) throws Exception {
        String token = token(email);
        jdbc.update("UPDATE user_account SET email_verified_at = now() WHERE email_normalized = ?", email);
        return token;
    }

    private String token(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }
}
