package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.MfaLoginChallenge;
import br.com.controlegastos.identity.domain.RecoveryCode;
import br.com.controlegastos.identity.infrastructure.MfaLoginChallengeRepository;
import br.com.controlegastos.identity.infrastructure.RecoveryCodeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecoveryLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final String IP = "203.0.113.11";
    private static final String RAW_CHALLENGE = "desafio";
    private static final String CODE = "ABCDE-23456";

    private final MfaLoginChallengeRepository challenges = mock(MfaLoginChallengeRepository.class);
    private final RecoveryCodeRepository codes = mock(RecoveryCodeRepository.class);
    private final RestrictedSessionTokenService restricted = mock(RestrictedSessionTokenService.class);
    private final AuthAttemptService attempts = mock(AuthAttemptService.class);
    private final RecoveryLoginService service =
            new RecoveryLoginService(challenges, codes, restricted, attempts, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final RestrictedSessionTokenService.RestrictedToken token =
            new RestrictedSessionTokenService.RestrictedToken("restrito", 600);
    private MfaLoginChallenge challenge;
    private RecoveryCode recoveryCode;

    @BeforeEach
    void setUp() {
        challenge = MfaLoginChallenge.issue(userId, Sha256.hex(RAW_CHALLENGE), NOW.minusSeconds(10), Duration.ofMinutes(5));
        when(challenges.findLockedByChallengeHash(Sha256.hex(RAW_CHALLENGE))).thenReturn(Optional.of(challenge));
        recoveryCode = RecoveryCode.issue(userId, Sha256.hex(CODE), NOW.minusSeconds(3600));
        when(codes.findLockedByUserIdAndCodeHash(userId, Sha256.hex(CODE))).thenReturn(Optional.of(recoveryCode));
        when(restricted.issueRecoverySetupToken(userId)).thenReturn(token);
    }

    @Test
    @DisplayName("código válido consome código e desafio e emite token restrito")
    void codigoValidoEmiteTokenRestrito() {
        assertThat(service.verify(RAW_CHALLENGE, CODE, IP)).isEqualTo(token);

        assertThat(recoveryCode.canBeConsumedAt(NOW)).isFalse();
        assertThat(challenge.canBeConsumedAt(NOW)).isFalse();
        verify(attempts).clearMfaRecoveryFailures(IP);
    }

    @Test
    @DisplayName("aceita o código com espaços e em minúsculas")
    void aceitaCodigoNormalizado() {
        assertThat(service.verify(RAW_CHALLENGE, "  abcde-23456 ", IP)).isEqualTo(token);
    }

    @Test
    @DisplayName("código já usado não emite token")
    void codigoReutilizadoFalha() {
        recoveryCode.consume(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.verify(RAW_CHALLENGE, CODE, IP))
                .isInstanceOf(InvalidRecoveryCodeException.class);
        verify(restricted, never()).issueRecoverySetupToken(any());
        assertThat(challenge.canBeConsumedAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("código invalidado por regeneração não emite token")
    void codigoInvalidadoFalha() {
        recoveryCode.invalidate(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.verify(RAW_CHALLENGE, CODE, IP))
                .isInstanceOf(InvalidRecoveryCodeException.class);
        verify(attempts).recordMfaRecoveryFailure(IP);
    }

    @Test
    @DisplayName("bloqueio por tentativas lança TooManyAttemptsException")
    void bloqueioPorTentativas() {
        doThrow(new TooManyAttemptsException()).when(attempts).assertMfaRecoveryAllowed(IP);

        assertThatThrownBy(() -> service.verify(RAW_CHALLENGE, CODE, IP))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(codes, never()).findLockedByUserIdAndCodeHash(any(), anyString());
    }

    @Test
    @DisplayName("desafio desconhecido lança InvalidRecoveryCodeException")
    void desafioDesconhecidoFalha() {
        assertThatThrownBy(() -> service.verify("outro-desafio", CODE, IP))
                .isInstanceOf(InvalidRecoveryCodeException.class);
    }

    @Test
    @DisplayName("código de outro titular lança InvalidRecoveryCodeException")
    void codigoDeOutroTitularFalha() {
        when(codes.findLockedByUserIdAndCodeHash(eq(userId), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(RAW_CHALLENGE, "ZZZZZ-99999", IP))
                .isInstanceOf(InvalidRecoveryCodeException.class);
    }

    @Test
    @DisplayName("código nulo lança InvalidRecoveryCodeException")
    void codigoNuloFalha() {
        assertThatThrownBy(() -> service.verify(RAW_CHALLENGE, null, IP))
                .isInstanceOf(InvalidRecoveryCodeException.class);
    }
}
