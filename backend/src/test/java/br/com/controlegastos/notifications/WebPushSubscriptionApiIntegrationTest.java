package br.com.controlegastos.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import br.com.controlegastos.support.TestVapidKeys;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** Assinaturas Web Push (spec notifications/web-push-delivery). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class WebPushSubscriptionApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String SUBSCRIPTIONS = "/api/v1/notifications/web-push/subscriptions";
    private static final TestVapidKeys VAPID = TestVapidKeys.generate();

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository authAttempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    @DisplayName("expõe só a chave pública VAPID, e exige sessão")
    void publicKey() throws Exception {
        String token = token("push-chave@example.com");

        mockMvc.perform(get("/api/v1/notifications/web-push/public-key")).andExpect(status().isUnauthorized());
        String body = mockMvc.perform(get("/api/v1/notifications/web-push/public-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value(VAPID.publicKey()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(VAPID.privateKey());
    }

    @Test
    @DisplayName("registrar de novo o mesmo endpoint atualiza as chaves sem duplicar")
    void upsertByEndpoint() throws Exception {
        String token = token("push-upsert@example.com");
        String endpoint = "https://push.example.com/send/upsert";

        register(token, endpoint, TestVapidKeys.browserKey(), TestVapidKeys.authSecret()).andExpect(status().isNoContent());
        String newAuth = TestVapidKeys.authSecret();
        register(token, endpoint, TestVapidKeys.browserKey(), newAuth).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM web_push_subscription WHERE endpoint = ?",
                Integer.class, endpoint)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT auth FROM web_push_subscription WHERE endpoint = ?",
                String.class, endpoint)).isEqualTo(newAuth);
    }

    @Test
    @DisplayName("endpoint registrado por outra conta passa a pertencer só à nova conta")
    void endpointMovesToNewAccount() throws Exception {
        String a = token("push-a@example.com");
        String b = token("push-b@example.com");
        String endpoint = "https://push.example.com/send/shared-browser";

        register(a, endpoint, TestVapidKeys.browserKey(), TestVapidKeys.authSecret()).andExpect(status().isNoContent());
        register(b, endpoint, TestVapidKeys.browserKey(), TestVapidKeys.authSecret()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
                SELECT u.email_normalized FROM web_push_subscription s JOIN user_account u ON u.id = s.user_id
                WHERE s.endpoint = ?
                """, String.class, endpoint)).isEqualTo("push-b@example.com");
    }

    @Test
    @DisplayName("recusa endpoint sem https e chaves mal formadas")
    void validation() throws Exception {
        String token = token("push-valida@example.com");

        register(token, "http://push.example.com/send/x", TestVapidKeys.browserKey(), TestVapidKeys.authSecret())
                .andExpect(status().isBadRequest());
        register(token, "https://push.example.com/send/x", "curta", TestVapidKeys.authSecret())
                .andExpect(status().isBadRequest());
        register(token, "https://push.example.com/send/x", TestVapidKeys.browserKey(), "abc")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM web_push_subscription", Integer.class)).isZero();
    }

    @Test
    @DisplayName("remover endpoint de outra conta responde 204 sem efeito; o dono consegue remover")
    void removal() throws Exception {
        String owner = token("push-dono@example.com");
        String intruder = token("push-intruso@example.com");
        String endpoint = "https://push.example.com/send/removal";
        register(owner, endpoint, TestVapidKeys.browserKey(), TestVapidKeys.authSecret()).andExpect(status().isNoContent());

        remove(intruder, endpoint).andExpect(status().isNoContent());
        assertThat(count(endpoint)).isEqualTo(1);

        remove(owner, endpoint).andExpect(status().isNoContent());
        assertThat(count(endpoint)).isZero();
        remove(owner, endpoint).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("exportação traz endpoint sem as chaves; exclusão da conta apaga a assinatura")
    void exportAndErasure() throws Exception {
        String token = token("push-titular@example.com");
        String endpoint = "https://push.example.com/send/titular";
        String auth = TestVapidKeys.authSecret();
        String key = TestVapidKeys.browserKey();
        register(token, endpoint, key, auth).andExpect(status().isNoContent());

        String export = mockMvc.perform(get("/api/v1/privacy/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webPushSubscriptions[0].endpoint").value(endpoint))
                .andExpect(jsonPath("$.webPushSubscriptions[0].createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(export).doesNotContain(auth).doesNotContain(key).doesNotContain("p256dh");

        mockMvc.perform(delete("/api/v1/privacy/account")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());
        assertThat(count(endpoint)).isZero();
    }

    private int count(String endpoint) {
        return jdbc.queryForObject("SELECT count(*) FROM web_push_subscription WHERE endpoint = ?", Integer.class, endpoint);
    }

    private ResultActions register(String token, String endpoint, String p256dh, String auth) throws Exception {
        return mockMvc.perform(put(SUBSCRIPTIONS)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "endpoint", endpoint, "keys", Map.of("p256dh", p256dh, "auth", auth)))));
    }

    private ResultActions remove(String token, String endpoint) throws Exception {
        return mockMvc.perform(post(SUBSCRIPTIONS + "/remove")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("endpoint", endpoint))));
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
