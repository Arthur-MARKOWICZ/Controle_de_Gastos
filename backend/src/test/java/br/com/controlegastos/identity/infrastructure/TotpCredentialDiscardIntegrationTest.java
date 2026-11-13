package br.com.controlegastos.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.TotpCredentialStatus;
import br.com.controlegastos.identity.domain.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Descarte de cadastros TOTP abandonados (retenção do ADR-0019). */
@SpringBootTest
@Testcontainers
class TotpCredentialDiscardIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TotpCredentialRepository credentials;
    @Autowired UserAccountRepository users;
    @Autowired TransactionTemplate transactions;

    private UUID user() {
        UserAccount user = UserAccount.register(EmailAddress.from(UUID.randomUUID() + "@example.com"), "hash", NOW);
        users.save(user);
        return user.id();
    }

    private UUID pending(Instant startedAt) {
        UUID userId = user();
        TotpCredential credential = TotpCredential.initiallyDisabled(userId, startedAt);
        credential.startEnrollment(new byte[] {1}, new byte[] {2}, 1, startedAt, Duration.ofMinutes(10));
        credentials.save(credential);
        return userId;
    }

    private int discard(Instant now) {
        return transactions.execute(status -> credentials.discardAbandonedPending(now));
    }

    private TotpCredential reload(UUID userId) {
        return credentials.findById(userId).orElseThrow();
    }

    @Test
    @DisplayName("cadastro pendente vencido volta a DISABLED e perde o segredo cifrado")
    void pendenteVencidoDescartado() {
        UUID userId = pending(NOW.minus(Duration.ofMinutes(11)));

        discard(NOW);

        assertThat(reload(userId).status()).isEqualTo(TotpCredentialStatus.DISABLED);
        assertThat(reload(userId).secretCiphertext()).isNull();
    }

    @Test
    @DisplayName("cadastro pendente ainda no prazo é mantido")
    void pendenteNoPrazoMantido() {
        UUID userId = pending(NOW.minus(Duration.ofMinutes(5)));

        discard(NOW);

        assertThat(reload(userId).status()).isEqualTo(TotpCredentialStatus.PENDING);
    }

    @Test
    @DisplayName("cadastro que vence exatamente agora ainda não é descartado")
    void venceExatamenteAgora() {
        UUID userId = pending(NOW.minus(Duration.ofMinutes(10)));

        discard(NOW);

        assertThat(reload(userId).status()).isEqualTo(TotpCredentialStatus.PENDING);
    }

    @Test
    @DisplayName("MFA habilitado nunca é descartado")
    void habilitadoMantido() {
        UUID userId = pending(NOW.minus(Duration.ofMinutes(30)));
        TotpCredential credential = reload(userId);
        credential.confirm(NOW.minus(Duration.ofMinutes(29)));
        credentials.save(credential);

        discard(NOW.plus(Duration.ofDays(365)));

        assertThat(reload(userId).status()).isEqualTo(TotpCredentialStatus.ENABLED);
        assertThat(reload(userId).secretCiphertext()).isNotNull();
    }

    @Test
    @DisplayName("descartar fora de transação lança erro de persistência")
    void descartarSemTransacaoFalha() {
        assertThatThrownBy(() -> credentials.discardAbandonedPending(NOW))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class);
    }

    @Test
    @DisplayName("credencial para usuário inexistente viola a chave estrangeira")
    void usuarioInexistenteViolaFk() {
        TotpCredential orphan = TotpCredential.initiallyDisabled(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> credentials.saveAndFlush(orphan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rodar o descarte duas vezes não altera mais nada na segunda")
    void descarteIdempotente() {
        pending(NOW.minus(Duration.ofHours(2)));
        discard(NOW);

        assertThat(discard(NOW)).isZero();
    }
}
