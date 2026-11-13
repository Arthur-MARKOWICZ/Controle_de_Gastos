package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthAttemptTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK = Duration.ofMinutes(15);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int LIMIT = 5;

    private AuthAttempt failTimes(int times, Instant at) {
        AuthAttempt attempt = new AuthAttempt("chave", NOW, RETENTION);
        for (int i = 0; i < times; i++) {
            attempt.recordFailure(at, WINDOW, BLOCK, RETENTION, LIMIT);
        }
        return attempt;
    }

    @Test
    @DisplayName("tentativa nova não está bloqueada")
    void tentativaNovaNaoBloqueada() {
        assertThat(new AuthAttempt("chave", NOW, RETENTION).isBlockedAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("quatro falhas na janela ainda não bloqueiam")
    void quatroFalhasNaoBloqueiam() {
        assertThat(failTimes(LIMIT - 1, NOW).isBlockedAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("a quinta falha na janela bloqueia pelo período de bloqueio")
    void quintaFalhaBloqueia() {
        AuthAttempt attempt = failTimes(LIMIT, NOW);

        assertThat(attempt.isBlockedAt(NOW)).isTrue();
        assertThat(attempt.isBlockedAt(NOW.plus(BLOCK).minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("bloqueio termina exatamente no fim do período")
    void bloqueioTerminaNoFim() {
        assertThat(failTimes(LIMIT, NOW).isBlockedAt(NOW.plus(BLOCK))).isFalse();
    }

    @Test
    @DisplayName("falha depois da janela reinicia a contagem")
    void falhaForaDaJanelaReiniciaContagem() {
        AuthAttempt attempt = failTimes(LIMIT - 1, NOW);

        attempt.recordFailure(NOW.plus(WINDOW), WINDOW, BLOCK, RETENTION, LIMIT);

        assertThat(attempt.isBlockedAt(NOW.plus(WINDOW))).isFalse();
    }

    @Test
    @DisplayName("nova janela limpa um bloqueio vencido")
    void novaJanelaLimpaBloqueio() {
        AuthAttempt attempt = failTimes(LIMIT, NOW);
        Instant later = NOW.plus(WINDOW).plus(BLOCK);

        attempt.recordFailure(later, WINDOW, BLOCK, RETENTION, LIMIT);

        assertThat(attempt.isBlockedAt(later)).isFalse();
    }

    @Test
    @DisplayName("cada falha renova o prazo de retenção do registro")
    void falhaRenovaRetencao() {
        AuthAttempt attempt = failTimes(1, NOW.plusSeconds(60));

        assertThat(attempt.expiresAt).isEqualTo(NOW.plusSeconds(60).plus(RETENTION));
    }
}
