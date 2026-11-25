package br.com.controlegastos.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.notifications.application.DailyReminderService;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import br.com.controlegastos.support.TestVapidKeys;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * Entrega de push de ponta a ponta contra um push service falso (spec
 * notifications/web-push-delivery e notifications/event-alerts). As assinaturas são
 * inseridas direto no banco porque o push service falso usa http local.
 * Relógio de teste: 2026-09-15 12:00 em São Paulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class PushDeliveryApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final TestVapidKeys VAPID = TestVapidKeys.generate();
    private static final Map<String, AtomicInteger> STATUS_BY_PATH = new ConcurrentHashMap<>();
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static HttpServer pushService;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.cookie-name", () -> "refresh_token");
        VAPID.register(registry);
    }

    @BeforeAll
    static void startPushService() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pushService.createContext("/push", exchange -> {
            String path = exchange.getRequestURI().getPath();
            exchange.getRequestBody().readAllBytes();
            if ("aes128gcm".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
                RECEIVED.add(path);
            }
            int code = STATUS_BY_PATH.getOrDefault(path, new AtomicInteger(201)).get();
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        pushService.start();
    }

    @AfterAll
    static void stopPushService() {
        pushService.stop(0);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository authAttempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired DailyReminderService dailyReminder;

    @BeforeEach
    void reset() {
        authAttempts.deleteAll();
        RECEIVED.clear();
        STATUS_BY_PATH.clear();
    }

    @Test
    @DisplayName("gasto de participante vira push para o dono só com SHARED_EXPENSE ativo")
    void sharedExpensePushRespectsConsent() throws Exception {
        String owner = verifiedToken("push-e2e-dono@example.com");
        String participant = verifiedToken("push-e2e-part@example.com");
        String envelopeId = createEnvelope(owner, "1000.00");
        invite(owner, envelopeId, "push-e2e-part@example.com");
        String path = subscribe("push-e2e-dono@example.com");

        expense(participant, envelopeId, "40.00");
        awaitAlerts(owner, 1);
        Thread.sleep(500);
        assertThat(RECEIVED).doesNotContain(path);

        preferences(owner, false, false, true, "20:00");
        expense(participant, envelopeId, "15.00");
        await().atMost(WAIT).untilAsserted(() -> assertThat(RECEIVED).containsExactly(path));
    }

    @Test
    @DisplayName("push service fora do ar não impede o gasto nem o alerta; o envio fica pendente")
    void failedPushStaysPending() throws Exception {
        String owner = token("push-e2e-falha@example.com");
        String envelopeId = createEnvelope(owner, "100.00");
        String path = subscribe("push-e2e-falha@example.com");
        STATUS_BY_PATH.put(path, new AtomicInteger(503));
        preferences(owner, false, true, false, "20:00");

        expense(owner, envelopeId, "150.00");

        awaitAlerts(owner, 1);
        await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE completion_date IS NULL AND listener_id LIKE '%PushAlertListener%'
                """, Integer.class)).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM web_push_subscription WHERE endpoint LIKE ?",
                Integer.class, "%" + path)).isEqualTo(1);
    }

    @Test
    @DisplayName("assinatura recusada com 410 é apagada")
    void goneSubscriptionIsDeleted() throws Exception {
        String owner = token("push-e2e-410@example.com");
        String envelopeId = createEnvelope(owner, "100.00");
        String path = subscribe("push-e2e-410@example.com");
        STATUS_BY_PATH.put(path, new AtomicInteger(410));
        preferences(owner, false, true, false, "20:00");

        expense(owner, envelopeId, "150.00");

        await().atMost(WAIT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM web_push_subscription WHERE endpoint LIKE ?", Integer.class, "%" + path))
                .isZero());
    }

    @Test
    @DisplayName("lembrete diário sai uma vez por dia e não sai para quem já lançou")
    void dailyReminder() throws Exception {
        String without = token("push-e2e-sem-gasto@example.com");
        String with = token("push-e2e-com-gasto@example.com");
        String withoutPath = subscribe("push-e2e-sem-gasto@example.com");
        String withPath = subscribe("push-e2e-com-gasto@example.com");
        preferences(without, true, false, false, "20:00");
        preferences(with, true, false, false, "20:00");
        expense(with, createEnvelope(with, "500.00"), "8.00");
        jdbc.update("""
                UPDATE notification_preference SET reminder_time = '12:00', last_reminder_date = NULL
                WHERE user_id IN (SELECT id FROM user_account WHERE email_normalized IN (?, ?))
                """, "push-e2e-sem-gasto@example.com", "push-e2e-com-gasto@example.com");

        dailyReminder.sendDue();
        dailyReminder.sendDue();

        assertThat(RECEIVED).containsExactly(withoutPath);
        assertThat(RECEIVED).doesNotContain(withPath);
    }

    @Test
    @DisplayName("ativar o lembrete depois do horário de hoje não dispara na rodada seguinte")
    void enablingLateSkipsToday() throws Exception {
        String token = token("push-e2e-tarde@example.com");
        String path = subscribe("push-e2e-tarde@example.com");

        preferences(token, true, false, false, "08:00");
        dailyReminder.sendDue();

        assertThat(RECEIVED).doesNotContain(path);
    }

    private String subscribe(String email) {
        String path = "/push/" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO web_push_subscription(id, user_id, endpoint, p256dh, auth, created_at)
                SELECT ?, id, ?, ?, ?, ? FROM user_account WHERE email_normalized = ?
                """, UUID.randomUUID(), "http://127.0.0.1:" + pushService.getAddress().getPort() + path,
                TestVapidKeys.browserKey(), TestVapidKeys.authSecret(),
                Timestamp.from(FixedClockTestConfiguration.NOW), email);
        return path;
    }

    private void preferences(String token, boolean daily, boolean negative, boolean shared, String time)
            throws Exception {
        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dailyReminder", daily, "negativeBalance", negative,
                                "sharedExpense", shared, "reminderTime", time))))
                .andExpect(status().isOk());
    }

    private void awaitAlerts(String token, int expected) {
        await().atMost(WAIT).untilAsserted(() -> assertThat(objectMapper.readTree(
                mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getContentAsString()).get("items").size()).isEqualTo(expected));
    }

    private void expense(String token, String envelopeId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/envelopes/{id}/entries", envelopeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "EXPENSE", "amount", Map.of("amount", amount, "currency", "BRL"),
                                "occurredAt", "2026-09-15"))))
                .andExpect(status().isCreated());
    }

    private void invite(String owner, String envelopeId, String email) throws Exception {
        mockMvc.perform(post("/api/v1/envelopes/{id}/participants", envelopeId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());
    }

    private String createEnvelope(String token, String baseAmount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Mercado", "purpose", "LIMIT",
                                "baseAmount", Map.of("amount", baseAmount, "currency", "BRL")))))
                .andExpect(status().isCreated()).andReturn();
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
