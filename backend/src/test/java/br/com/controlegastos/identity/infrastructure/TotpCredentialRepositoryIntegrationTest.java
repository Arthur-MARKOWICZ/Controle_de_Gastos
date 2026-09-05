package br.com.controlegastos.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"spring.flyway.target=9", "spring.jpa.hibernate.ddl-auto=none"})
@Testcontainers
class TotpCredentialRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void migrationBackfillsAUserThatExistedBeforeMfaWasIntroducedAsDisabled() {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO user_account "
                        + "(id, email_normalized, status, created_at, updated_at, password_hash, password_changed_at) "
                        + "VALUES (?, ?, 'ACTIVE', ?, ?, 'hash', ?)",
                userId, "pre-existente@example.com", now, now, now
        );

        Flyway.configure().dataSource(dataSource).load().migrate();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM totp_credential WHERE user_id = ?", String.class, userId);
        assertThat(status).isEqualTo("DISABLED");
    }
}
