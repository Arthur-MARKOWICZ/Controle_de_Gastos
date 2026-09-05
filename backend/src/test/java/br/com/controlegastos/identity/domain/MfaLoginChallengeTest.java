package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MfaLoginChallengeTest {

    private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    @Test
    void canBeConsumedOnlyOnceBeforeExpiration() {
        MfaLoginChallenge challenge = MfaLoginChallenge.issue(UUID.randomUUID(), "hash", START, LIFETIME);

        assertThat(challenge.canBeConsumedAt(START)).isTrue();
        challenge.consume(START.plusSeconds(1));
        assertThat(challenge.canBeConsumedAt(START.plusSeconds(2))).isFalse();
        assertThatThrownBy(() -> challenge.consume(START.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiresAfterItsLifetime() {
        MfaLoginChallenge challenge = MfaLoginChallenge.issue(UUID.randomUUID(), "hash", START, LIFETIME);

        assertThat(challenge.canBeConsumedAt(START.plus(LIFETIME).minusSeconds(1))).isTrue();
        assertThat(challenge.canBeConsumedAt(START.plus(LIFETIME).plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> challenge.consume(START.plus(LIFETIME).plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
