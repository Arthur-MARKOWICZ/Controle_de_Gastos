package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OAuthAuthorizationStateTest {

    private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(10);

    @Test
    void canBeConsumedOnlyOnceBeforeExpiration() {
        OAuthAuthorizationState state = OAuthAuthorizationState.issue("hash", OAuthProvider.GOOGLE, null, START, LIFETIME);

        assertThat(state.canBeConsumedAt(START)).isTrue();
        state.consume(START.plusSeconds(1));
        assertThat(state.canBeConsumedAt(START.plusSeconds(2))).isFalse();
        assertThatThrownBy(() -> state.consume(START.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiresAfterItsLifetime() {
        OAuthAuthorizationState state = OAuthAuthorizationState.issue("hash", OAuthProvider.GITHUB, null, START, LIFETIME);

        assertThat(state.canBeConsumedAt(START.plus(LIFETIME).minusSeconds(1))).isTrue();
        assertThat(state.canBeConsumedAt(START.plus(LIFETIME).plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> state.consume(START.plus(LIFETIME).plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsTheLinkingUserIdWhenProvidedForAConnectFlow() {
        UUID linkingUserId = UUID.randomUUID();

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(
                "hash", OAuthProvider.GOOGLE, linkingUserId, START, LIFETIME);

        assertThat(state.linkingUserId()).isEqualTo(linkingUserId);
    }

    @Test
    void hasNoLinkingUserIdForALoginOrRegisterFlow() {
        OAuthAuthorizationState state = OAuthAuthorizationState.issue("hash", OAuthProvider.GOOGLE, null, START, LIFETIME);

        assertThat(state.linkingUserId()).isNull();
    }
}
