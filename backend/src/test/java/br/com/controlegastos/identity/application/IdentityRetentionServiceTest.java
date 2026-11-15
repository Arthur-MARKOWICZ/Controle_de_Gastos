package br.com.controlegastos.identity.application;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
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

    private final AuthAttemptRepository attempts = mock(AuthAttemptRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final MfaLoginChallengeRepository challenges = mock(MfaLoginChallengeRepository.class);
    private final RecoveryCodeRepository recoveryCodes = mock(RecoveryCodeRepository.class);
    private final TotpCredentialRepository totp = mock(TotpCredentialRepository.class);
    private final OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
    private final OAuthMobileHandoffRepository handoffs = mock(OAuthMobileHandoffRepository.class);
    private final IdentityRetentionService service = new IdentityRetentionService(attempts, sessions, resetTokens,
            challenges, recoveryCodes, totp, states, handoffs, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("sessões encerradas ficam 30 dias para investigação antes do descarte")
    void sessoesFicamTrintaDias() {
        service.discardExpiredTechnicalData();

        verify(sessions).deleteEndedBefore(Instant.parse("2026-08-16T15:00:00Z"));
    }

    @Test
    @DisplayName("rodar duas vezes no mesmo instante usa os mesmos cortes")
    void execucaoIdempotente() {
        service.discardExpiredTechnicalData();
        service.discardExpiredTechnicalData();

        verify(mobileHandoffs(), times(2)).deleteEndedBefore(NOW.minus(Duration.ofHours(24)));
    }

    private OAuthMobileHandoffRepository mobileHandoffs() {
        return handoffs;
    }

    @Test
    @DisplayName("falha do banco ao apagar sessões interrompe o descarte e propaga a exceção")
    void falhaInterrompeDescarte() {
        when(sessions.deleteEndedBefore(any())).thenThrow(new DataAccessResourceFailureException("banco fora"));

        assertThatThrownBy(service::discardExpiredTechnicalData)
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("banco fora");
        verify(resetTokens, never()).deleteEndedBefore(any());
    }

    @Test
    @DisplayName("falha no primeiro passo não apaga nenhum outro dado")
    void falhaNoPrimeiroPasso() {
        when(attempts.deleteExpired(any())).thenThrow(new DataAccessResourceFailureException("lock"));

        assertThatThrownBy(service::discardExpiredTechnicalData).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoMoreInteractions(sessions, resetTokens, challenges, recoveryCodes, totp, states, handoffs);
    }

    @Test
    @DisplayName("roda diariamente às 03:17 UTC em transação única")
    void agendamentoETransacao() throws Exception {
        var method = IdentityRetentionService.class.getMethod("discardExpiredTechnicalData");

        assertThat(method.getAnnotation(Scheduled.class).cron()).isEqualTo("0 17 3 * * *");
        assertThat(method.getAnnotation(Scheduled.class).zone()).isEqualTo("UTC");
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("credenciais TOTP pendentes são descartadas pelo instante atual, sem margem")
    void pendentesPeloInstanteAtual() {
        service.discardExpiredTechnicalData();

        verify(totp).discardAbandonedPending(NOW);
    }
}
