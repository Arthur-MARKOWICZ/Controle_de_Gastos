package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.MfaLoginChallenge;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.infrastructure.MfaLoginChallengeRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.TotpSecretCipher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MfaLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final String IP = "203.0.113.10";
    private static final String RAW = "desafio-bruto";

    private final MfaLoginChallengeRepository challenges = mock(MfaLoginChallengeRepository.class);
    private final TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
    private final TotpSecretCipher cipher = mock(TotpSecretCipher.class);
    private final TotpService totp = mock(TotpService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AuthAttemptService attempts = mock(AuthAttemptService.class);
    private final MfaLoginService service = new MfaLoginService(challenges, credentials, cipher, totp, sessions,
            attempts, Clock.fixed(NOW, ZoneOffset.UTC), LIFETIME);
    private final UUID userId = UUID.randomUUID();
    private final SessionService.AuthenticatedSession session = new SessionService.AuthenticatedSession("a", 900, "r");

    @BeforeEach
    void setUp() {
        when(cipher.decrypt(any(), any(), anyInt())).thenReturn("SEGREDO");
        when(sessions.start(userId)).thenReturn(session);
    }

    private MfaLoginChallenge challenge(Instant issuedAt) {
        MfaLoginChallenge challenge = MfaLoginChallenge.issue(userId, Sha256.hex(RAW), issuedAt, LIFETIME);
        when(challenges.findLockedByChallengeHash(Sha256.hex(RAW))).thenReturn(Optional.of(challenge));
        return challenge;
    }

    private void mfaEnabled() {
        TotpCredential credential = TotpCredential.initiallyDisabled(userId, NOW);
        credential.startEnrollment(new byte[] {1}, new byte[] {2}, 1, NOW.minusSeconds(60), Duration.ofMinutes(10));
        credential.confirm(NOW.minusSeconds(30));
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
    }

    @Test
    @DisplayName("cria desafio guardando só o hash e informando o prazo em segundos")
    void criaDesafioComHash() {
        MfaLoginService.ChallengeIssued issued = service.createChallenge(userId);

        ArgumentCaptor<MfaLoginChallenge> saved = ArgumentCaptor.forClass(MfaLoginChallenge.class);
        verify(challenges).save(saved.capture());
        assertThat(issued.expiresIn()).isEqualTo(300);
        assertThat(issued.challengeId()).isNotBlank();
        assertThat(saved.getValue().canBeConsumedAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("código válido consome o desafio e inicia a sessão")
    void codigoValidoIniciaSessao() {
        MfaLoginChallenge challenge = challenge(NOW.minusSeconds(10));
        mfaEnabled();
        when(totp.verifyCode("SEGREDO", "123456")).thenReturn(true);

        assertThat(service.verify(RAW, "123456", IP)).isEqualTo(session);
        assertThat(challenge.canBeConsumedAt(NOW)).isFalse();
        verify(attempts).clearMfaVerifyFailures(IP);
    }

    @Test
    @DisplayName("desafio já consumido não emite segunda sessão")
    void desafioReutilizadoFalha() {
        challenge(NOW.minusSeconds(10)).consume(NOW.minusSeconds(5));
        mfaEnabled();

        assertThatThrownBy(() -> service.verify(RAW, "123456", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
        verify(sessions, never()).start(any());
    }

    @Test
    @DisplayName("MFA desabilitado depois do desafio impede o login")
    void mfaDesabilitadoImpedeLogin() {
        challenge(NOW.minusSeconds(10));
        when(credentials.findById(userId)).thenReturn(Optional.of(TotpCredential.initiallyDisabled(userId, NOW)));

        assertThatThrownBy(() -> service.verify(RAW, "123456", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
    }

    @Test
    @DisplayName("bloqueio por tentativas lança TooManyAttemptsException antes de consultar o desafio")
    void bloqueioPorTentativas() {
        doThrow(new TooManyAttemptsException()).when(attempts).assertMfaVerifyAllowed(IP);

        assertThatThrownBy(() -> service.verify(RAW, "123456", IP))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(challenges, never()).findLockedByChallengeHash(anyString());
    }

    @Test
    @DisplayName("desafio expirado lança InvalidMfaChallengeException e registra falha")
    void desafioExpiradoFalha() {
        challenge(NOW.minus(LIFETIME).minusSeconds(1));

        assertThatThrownBy(() -> service.verify(RAW, "123456", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
        verify(attempts).recordMfaVerifyFailure(IP);
    }

    @Test
    @DisplayName("código TOTP errado lança InvalidMfaChallengeException e mantém o desafio")
    void codigoErradoFalha() {
        MfaLoginChallenge challenge = challenge(NOW.minusSeconds(10));
        mfaEnabled();
        when(totp.verifyCode("SEGREDO", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.verify(RAW, "000000", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
        assertThat(challenge.canBeConsumedAt(NOW)).isTrue();
        verify(attempts).recordMfaVerifyFailure(IP);
    }

    @Test
    @DisplayName("desafio desconhecido lança InvalidMfaChallengeException")
    void desafioDesconhecidoFalha() {
        when(challenges.findLockedByChallengeHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("outro", "123456", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
    }
}
