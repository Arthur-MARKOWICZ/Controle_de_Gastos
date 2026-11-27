package br.com.controlegastos.investments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.investments.domain.StockQuote;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Persistência da última cotação por ticker (ADR-0035, design D2, D5 e D6). */
@SpringBootTest
@Testcontainers
class StockQuoteRepositoryIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-11-26T19:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-26T19:30:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired StockQuoteRepository quotes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM stock_quote");
        jdbc.update("DELETE FROM stock_lot");
    }

    @Test
    @DisplayName("upsert grava e só substitui por cotação mais nova ou igual")
    void upsertNeverGoesBackInTime() {
        quotes.upsert("PETR4", new BigDecimal("41.20"), T2, T2);
        quotes.upsert("PETR4", new BigDecimal("39.00"), T1, T2);
        assertThat(priceOf("PETR4")).isEqualByComparingTo("41.20");

        quotes.upsert("PETR4", new BigDecimal("41.50"), T2.plusSeconds(1800), T2);
        StockQuote quote = quotes.findById("PETR4").orElseThrow();
        assertThat(quote.price().toPlainString()).isEqualTo("41.50");
        assertThat(quote.quotedAt()).isEqualTo(T2.plusSeconds(1800));
    }

    @Test
    @DisplayName("tickers mantidos são distintos e de qualquer titular; faltantes excluem os cotados")
    void heldAndMissingTickers() {
        UUID a = user("cot-a@example.com");
        UUID b = user("cot-b@example.com");
        lot(a, "VALE3");
        lot(a, "PETR4");
        lot(b, "PETR4");
        lot(b, "ITUB4");
        quotes.upsert("PETR4", new BigDecimal("41.20"), T1, T1);

        assertThat(quotes.findHeldTickers()).containsExactly("ITUB4", "PETR4", "VALE3");
        assertThat(quotes.findMissingTickers()).containsExactly("ITUB4", "VALE3");
    }

    @Test
    @DisplayName("limpeza apaga só cotações de tickers sem lote")
    void deletesOrphans() {
        lot(user("cot-orfa@example.com"), "PETR4");
        quotes.upsert("PETR4", new BigDecimal("41.20"), T1, T1);
        quotes.upsert("MGLU3", new BigDecimal("9.10"), T1, T1);

        assertThat(quotes.deleteOrphans()).isEqualTo(1);
        assertThat(quotes.findById("MGLU3")).isEmpty();
        assertThat(quotes.findById("PETR4")).isPresent();
    }

    private BigDecimal priceOf(String ticker) {
        return jdbc.queryForObject("SELECT price FROM stock_quote WHERE ticker = ?", BigDecimal.class, ticker);
    }

    private UUID user(String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(T1);
        jdbc.update("INSERT INTO user_account (id, email_normalized, status, created_at, updated_at) "
                + "VALUES (?, ?, 'ACTIVE', ?, ?)", id, email, now, now);
        return id;
    }

    private void lot(UUID owner, String ticker) {
        jdbc.update("INSERT INTO stock_lot (id, owner_id, ticker, quantity, unit_price, purchase_date, created_at) "
                        + "VALUES (?, ?, ?, 10, 10.00, ?, ?)",
                UUID.randomUUID(), owner, ticker, java.sql.Date.valueOf(LocalDate.of(2026, 11, 3)), Timestamp.from(T1));
    }
}
