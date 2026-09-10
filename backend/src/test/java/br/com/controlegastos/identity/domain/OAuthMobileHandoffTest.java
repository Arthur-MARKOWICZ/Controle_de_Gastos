package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OAuthMobileHandoffTest {

    private static final Instant START = Instant.parse("2026-09-09T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofSeconds(60);
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void canBeConsumedOnlyOnce() {
        OAuthMobileHandoff handoff = OAuthMobileHandoff.issue(USER_ID, "hash", START, LIFETIME);

        assertThat(handoff.canBeConsumedAt(START)).isTrue();
        handoff.consume(START.plusSeconds(1));

        assertThat(handoff.canBeConsumedAt(START.plusSeconds(2))).isFalse();
        assertThatThrownBy(() -> handoff.consume(START.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiresAfterItsShortLifetime() {
        OAuthMobileHandoff handoff = OAuthMobileHandoff.issue(USER_ID, "hash", START, LIFETIME);

        assertThat(handoff.canBeConsumedAt(START.plus(LIFETIME).minusSeconds(1))).isTrue();
        assertThat(handoff.canBeConsumedAt(START.plus(LIFETIME).plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> handoff.consume(START.plus(LIFETIME).plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void carriesTheUserItAuthenticates() {
        assertThat(OAuthMobileHandoff.issue(USER_ID, "hash", START, LIFETIME).userId()).isEqualTo(USER_ID);
    }
}
