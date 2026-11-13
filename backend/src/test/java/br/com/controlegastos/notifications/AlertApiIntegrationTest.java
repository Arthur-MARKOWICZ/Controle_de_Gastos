package br.com.controlegastos.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** Contrato HTTP de {@code /api/v1/alerts} (spec notifications/alerts-api). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class AlertApiIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    @DisplayName("lista os alertas ativos do titular com mês de referência no formato AAAA-MM")
    void listaAlertasAtivosDoTitular() throws Exception {
        String token = tokenFor("alerta-lista@example.com");
        UUID envelopeId = UUID.randomUUID();
        UUID alertId = insertAlert(userId("alerta-lista@example.com"), envelopeId, "Mercado");

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(alertId.toString()))
                .andExpect(jsonPath("$.items[0].type").value("NEGATIVE_BALANCE"))
                .andExpect(jsonPath("$.items[0].envelopeId").value(envelopeId.toString()))
                .andExpect(jsonPath("$.items[0].envelopeName").value("Mercado"))
                .andExpect(jsonPath("$.items[0].referenceMonth").value("2026-09"))
                .andExpect(jsonPath("$.items[0].createdAt").exists());
    }

    @Test
    @DisplayName("não lista alertas de outro titular")
    void naoListaAlertasDeOutroTitular() throws Exception {
        String tokenA = tokenFor("alerta-a@example.com");
        tokenFor("alerta-b@example.com");
        insertAlert(userId("alerta-b@example.com"), UUID.randomUUID(), "Lazer de B");

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("dispensa o alerta do titular, que some da listagem")
    void dispensaAlertaQueSomeDaListagem() throws Exception {
        String token = tokenFor("alerta-dispensa@example.com");
        UUID alertId = insertAlert(userId("alerta-dispensa@example.com"), UUID.randomUUID(), "Mercado");

        mockMvc.perform(post("/api/v1/alerts/{id}/dismiss", alertId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT dismissed_at FROM in_app_alert WHERE id = ?", Timestamp.class, alertId))
                .isNotNull();
    }

    @Test
    @DisplayName("alerta resolvido não aparece na listagem")
    void alertaResolvidoNaoApareceNaListagem() throws Exception {
        String token = tokenFor("alerta-resolvido@example.com");
        UUID alertId = insertAlert(userId("alerta-resolvido@example.com"), UUID.randomUUID(), "Mercado");
        jdbc.update("UPDATE in_app_alert SET resolved_at = ? WHERE id = ?",
                Timestamp.from(FixedClockTestConfiguration.NOW), alertId);

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("recusa dispensar alerta de outro titular com 404 e mantém o alerta ativo")
    void recusaDispensarAlertaDeOutroTitular() throws Exception {
        String tokenA = tokenFor("alerta-intruso@example.com");
        tokenFor("alerta-dono@example.com");
        UUID alertId = insertAlert(userId("alerta-dono@example.com"), UUID.randomUUID(), "Mercado");

        mockMvc.perform(post("/api/v1/alerts/{id}/dismiss", alertId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Alerta não encontrado"));

        assertThat(jdbc.queryForObject("SELECT dismissed_at FROM in_app_alert WHERE id = ?", Timestamp.class, alertId))
                .isNull();
    }

    @Test
    @DisplayName("responde 404 com a mesma mensagem para alerta inexistente")
    void respondeNotFoundParaAlertaInexistente() throws Exception {
        String token = tokenFor("alerta-inexistente@example.com");

        mockMvc.perform(post("/api/v1/alerts/{id}/dismiss", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Alerta não encontrado"));
    }

    @Test
    @DisplayName("responde 400 para identificador de alerta que não é UUID")
    void respondeBadRequestParaIdMalformado() throws Exception {
        String token = tokenFor("alerta-malformado@example.com");

        mockMvc.perform(post("/api/v1/alerts/{id}/dismiss", "nao-e-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("exige sessão para listar e dispensar alertas")
    void exigeSessao() throws Exception {
        mockMvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/alerts/{id}/dismiss", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private UUID insertAlert(UUID ownerId, UUID envelopeId, String envelopeName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO in_app_alert(id, owner_id, envelope_id, envelope_name, alert_type, reference_month, created_at)
                VALUES (?, ?, ?, ?, 'NEGATIVE_BALANCE', ?, ?)
                """, id, ownerId, envelopeId, envelopeName, Date.valueOf(LocalDate.of(2026, 9, 1)),
                Timestamp.from(FixedClockTestConfiguration.NOW));
        return id;
    }

    private String tokenFor(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(new Credentials(email, PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
    }

    private record Credentials(String email, String password) {}
}
