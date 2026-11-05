package br.com.controlegastos.identity.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.identity.infrastructure.MfaLoginChallengeRepository;
import br.com.controlegastos.identity.infrastructure.OAuthAuthorizationStateRepository;
import br.com.controlegastos.identity.infrastructure.OAuthMobileHandoffRepository;
import br.com.controlegastos.identity.infrastructure.PasswordResetTokenRepository;
import br.com.controlegastos.identity.infrastructure.RecoveryCodeRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IdentityRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");

    @Test
    void discardExpiredTechnicalDataAlsoPurgesMobileHandoffsAfterRetentionWindow() {
        AuthAttemptRepository attempts = mock(AuthAttemptRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        MfaLoginChallengeRepository mfaLoginChallenges = mock(MfaLoginChallengeRepository.class);
        RecoveryCodeRepository recoveryCodes = mock(RecoveryCodeRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        OAuthAuthorizationStateRepository oauthAuthorizationStates = mock(OAuthAuthorizationStateRepository.class);
        OAuthMobileHandoffRepository mobileHandoffs = mock(OAuthMobileHandoffRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        IdentityRetentionService retention = new IdentityRetentionService(
                attempts, sessions, passwordResetTokens, mfaLoginChallenges, recoveryCodes,
                totpCredentials, oauthAuthorizationStates, mobileHandoffs, clock);

        retention.discardExpiredTechnicalData();

        Instant mfaCutoff = NOW.minus(Duration.ofHours(24));
        verify(attempts).deleteExpired(NOW);
        verify(sessions).deleteEndedBefore(NOW.minus(Duration.ofDays(30)));
        verify(passwordResetTokens).deleteEndedBefore(mfaCutoff);
        verify(mfaLoginChallenges).deleteEndedBefore(mfaCutoff);
        verify(recoveryCodes).deleteEndedBefore(mfaCutoff);
        verify(totpCredentials).discardAbandonedPending(NOW);
        verify(oauthAuthorizationStates).deleteEndedBefore(mfaCutoff);
        verify(mobileHandoffs).deleteEndedBefore(mfaCutoff);
        verifyNoMoreInteractions(
                attempts, sessions, passwordResetTokens, mfaLoginChallenges, recoveryCodes,
                totpCredentials, oauthAuthorizationStates, mobileHandoffs);
    }
}
