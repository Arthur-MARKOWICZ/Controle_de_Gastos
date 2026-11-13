package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthSessionTest {

    private static final Instant START = Instant.parse("2026-08-27T12:00:00Z");

    @Test
    void createsThirtyDayIdleAndOneYearAbsoluteLimits() {
        AuthSession session = AuthSession.start(
                UUID.randomUUID(),
                "hash-inicial",
                START,
                Duration.ofDays(30),
                Duration.ofDays(365)
        );

        assertThat(session.idleExpiresAt()).isEqualTo(START.plus(Duration.ofDays(30)));
        assertThat(session.absoluteExpiresAt()).isEqualTo(START.plus(Duration.ofDays(365)));
        assertThat(session.isActiveAt(START.plus(Duration.ofDays(29)))).isTrue();
        assertThat(session.isActiveAt(START.plus(Duration.ofDays(31)))).isFalse();
    }

    @Test
    void rotationNeverMovesIdleExpirationPastAbsoluteLimit() {
        AuthSession session = AuthSession.start(
                UUID.randomUUID(),
                "hash-inicial",
                START,
                Duration.ofDays(30),
                Duration.ofDays(365)
        );

        for (int day = 29; day <= 348; day += 29) {
            session.rotate("hash-" + day, START.plus(Duration.ofDays(day)), Duration.ofDays(30));
        }
        session.rotate("hash-novo", START.plus(Duration.ofDays(350)), Duration.ofDays(30));

        assertThat(session.idleExpiresAt()).isEqualTo(START.plus(Duration.ofDays(365)));
        assertThat(session.matchesRefreshHash("hash-novo")).isTrue();
    }

    @Test
    void revocationMakesSessionImmediatelyInactive() {
        AuthSession session = AuthSession.start(
                UUID.randomUUID(),
                "hash-inicial",
                START,
                Duration.ofDays(30),
                Duration.ofDays(365)
        );

        session.revoke(START.plusSeconds(1), "LOGOUT");

        assertThat(session.isActiveAt(START.plusSeconds(2))).isFalse();
    }

    private AuthSession session() {
        return AuthSession.start(UUID.randomUUID(), "hash-inicial", START, Duration.ofDays(30), Duration.ofDays(365));
    }

    @Test
    @DisplayName("sessão expira exatamente no limite ocioso")
    void expiraNoLimiteOcioso() {
        assertThat(session().isActiveAt(START.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test
    @DisplayName("rotacionar sessão revogada lança IllegalStateException")
    void rotacionarRevogadaFalha() {
        AuthSession session = session();
        session.revoke(START, "LOGOUT");

        assertThatThrownBy(() -> session.rotate("novo", START.plusSeconds(1), Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Sessão expirada ou revogada");
    }

    @Test
    @DisplayName("rotacionar sessão expirada lança IllegalStateException")
    void rotacionarExpiradaFalha() {
        AuthSession session = session();

        assertThatThrownBy(() -> session.rotate("novo", START.plus(Duration.ofDays(31)), Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("hash anterior deixa de casar depois da rotação")
    void hashAnteriorNaoCasa() {
        AuthSession session = session();
        session.rotate("hash-novo", START.plusSeconds(1), Duration.ofDays(30));

        assertThat(session.matchesRefreshHash("hash-inicial")).isFalse();
        assertThat(session.matchesRefreshHash(null)).isFalse();
    }

    @Test
    @DisplayName("revogar duas vezes mantém a primeira data")
    void revogarDuasVezes() {
        AuthSession session = session();
        session.revoke(START.plusSeconds(1), "LOGOUT");
        session.revoke(START.plusSeconds(9), "REFRESH_REUSE");

        assertThat(session.revokedAt()).isEqualTo(START.plusSeconds(1));
    }

    @Test
    @DisplayName("iniciar sem usuário lança NullPointerException")
    void semUsuarioFalha() {
        assertThatThrownBy(() -> AuthSession.start(null, "h", START, Duration.ofDays(1), Duration.ofDays(2)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rotacionar com hash nulo lança NullPointerException")
    void rotacionarHashNuloFalha() {
        AuthSession session = session();

        assertThatThrownBy(() -> session.rotate(null, START.plusSeconds(1), Duration.ofDays(30)))
                .isInstanceOf(NullPointerException.class);
    }
}
