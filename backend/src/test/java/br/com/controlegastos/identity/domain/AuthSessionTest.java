package br.com.controlegastos.identity.domain;

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
}
