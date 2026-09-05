package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotpCredentialTest {

    private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");
    private static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);

    @Test
    void startsDisabledForANewUser() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.DISABLED);
        assertThat(credential.requiresMfaAtLogin()).isFalse();
    }

    @Test
    void startingEnrollmentMovesToPendingWithSecretAndExpiration() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);

        credential.startEnrollment("cipher-1".getBytes(), "nonce-1".getBytes(), 1, START, PENDING_LIFETIME);

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.PENDING);
        assertThat(credential.pendingExpiresAt()).isEqualTo(START.plus(PENDING_LIFETIME));
        assertThat(credential.secretCiphertext()).isEqualTo("cipher-1".getBytes());
    }

    @Test
    void startingEnrollmentAgainInvalidatesThePreviousPendingSecret() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);
        credential.startEnrollment("cipher-1".getBytes(), "nonce-1".getBytes(), 1, START, PENDING_LIFETIME);

        credential.startEnrollment("cipher-2".getBytes(), "nonce-2".getBytes(), 1, START.plusSeconds(1), PENDING_LIFETIME);

        assertThat(credential.secretCiphertext()).isEqualTo("cipher-2".getBytes());
        assertThat(credential.pendingExpiresAt()).isEqualTo(START.plusSeconds(1).plus(PENDING_LIFETIME));
    }

    @Test
    void startingEnrollmentWhileAlreadyEnabledThrows() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);
        credential.startEnrollment("cipher-1".getBytes(), "nonce-1".getBytes(), 1, START, PENDING_LIFETIME);
        credential.confirm(START.plusSeconds(1));

        assertThatThrownBy(() ->
                credential.startEnrollment("cipher-2".getBytes(), "nonce-2".getBytes(), 1, START, PENDING_LIFETIME))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmingBeforeExpirationEnablesTheCredential() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);
        credential.startEnrollment("cipher-1".getBytes(), "nonce-1".getBytes(), 1, START, PENDING_LIFETIME);

        credential.confirm(START.plus(PENDING_LIFETIME).minusSeconds(1));

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.ENABLED);
        assertThat(credential.requiresMfaAtLogin()).isTrue();
        assertThat(credential.pendingExpiresAt()).isNull();
    }

    @Test
    void confirmingAfterExpirationThrowsAndLeavesStatusUnchanged() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);
        credential.startEnrollment("cipher-1".getBytes(), "nonce-1".getBytes(), 1, START, PENDING_LIFETIME);

        assertThatThrownBy(() -> credential.confirm(START.plus(PENDING_LIFETIME).plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.PENDING);
    }

    @Test
    void disablingClearsSecretMaterial() {
        TotpCredential credential = TotpCredential.initiallyDisabled(UUID.randomUUID(), START);
        credential.startEnrollment("cipher-1".getBytes(), "nonce-1".getBytes(), 1, START, PENDING_LIFETIME);
        credential.confirm(START.plusSeconds(1));

        credential.disable(START.plusSeconds(2));

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.DISABLED);
        assertThat(credential.secretCiphertext()).isNull();
        assertThat(credential.secretNonce()).isNull();
        assertThat(credential.keyVersion()).isNull();
        assertThat(credential.confirmedAt()).isNull();
    }
}
