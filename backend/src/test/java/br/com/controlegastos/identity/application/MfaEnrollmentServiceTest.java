package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.RecoveryCode;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.TotpCredentialStatus;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.RecoveryCodeRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.TotpSecretCipher;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class MfaEnrollmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final Duration PENDING = Duration.ofMinutes(10);
    private static final String PASSWORD = "frase segura de teste";
    private static final String IP = "203.0.113.9";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
    private final RecoveryCodeRepository recoveryCodes = mock(RecoveryCodeRepository.class);
    private final TotpSecretCipher cipher = mock(TotpSecretCipher.class);
    private final TotpService totp = mock(TotpService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AuthAttemptService attempts = mock(AuthAttemptService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MfaEnrollmentService service = new MfaEnrollmentService(users, credentials, recoveryCodes, cipher,
            totp, sessions, attempts, encoder, Clock.fixed(NOW, ZoneOffset.UTC), PENDING);
    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = UserAccount.register(EmailAddress.from("mfa-unit@example.com"), "hash", NOW);
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(encoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(totp.generateEnrollmentMaterial(user.id()))
                .thenReturn(new TotpService.EnrollmentMaterial("SEGREDO", "otpauth://totp/x", "data:image/png;base64,"));
        when(cipher.encrypt("SEGREDO")).thenReturn(new TotpSecretCipher.EncryptedSecret(new byte[] {1}, new byte[] {2}, 1));
        when(cipher.decrypt(any(), any(), anyInt())).thenReturn("SEGREDO");
    }

    private TotpCredential pending() {
        TotpCredential credential = TotpCredential.initiallyDisabled(user.id(), NOW);
        credential.startEnrollment(new byte[] {1}, new byte[] {2}, 1, NOW.minusSeconds(30), PENDING);
        when(credentials.findById(user.id())).thenReturn(Optional.of(credential));
        return credential;
    }

    private TotpCredential enabled() {
        TotpCredential credential = pending();
        credential.confirm(NOW.minusSeconds(10));
        return credential;
    }

    @Test
    @DisplayName("iniciar cadastro com a senha correta deixa a credencial pendente com prazo")
    void iniciarCadastroDeixaPendente() {
        when(credentials.findById(user.id())).thenReturn(Optional.empty());

        MfaEnrollmentService.EnrollmentStart start = service.start(user.id(), PASSWORD, IP, false);

        assertThat(start.manualEntryKey()).isEqualTo("SEGREDO");
        assertThat(start.pendingExpiresAt()).isEqualTo(NOW.plus(PENDING));
        verify(credentials).save(any(TotpCredential.class));
        verify(attempts).clearMfaSetupFailures(IP);
    }

    @Test
    @DisplayName("confirmar com código válido habilita o MFA, emite 10 códigos e revoga as sessões")
    void confirmarHabilitaEEmiteCodigos() {
        TotpCredential credential = pending();
        when(totp.verifyCode("SEGREDO", "123456")).thenReturn(true);

        List<String> codes = service.confirm(user.id(), "123456", IP);

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.ENABLED);
        assertThat(codes).hasSize(10);
        verify(recoveryCodes).invalidateActiveForUserId(user.id(), NOW);
        verify(recoveryCodes, times(10)).save(any(RecoveryCode.class));
        verify(sessions).revokeAllForMfaChange(user.id());
    }

    @Test
    @DisplayName("sessão restrita de recuperação substitui um segredo já habilitado")
    void sessaoRestritaSubstituiSegredo() {
        TotpCredential credential = enabled();

        service.start(user.id(), PASSWORD, IP, true);

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.PENDING);
    }

    @Test
    @DisplayName("senha errada registra falha de tentativa e lança InvalidPasswordConfirmationException")
    void senhaErradaRegistraFalha() {
        assertThatThrownBy(() -> service.start(user.id(), "errada", IP, false))
                .isInstanceOf(InvalidPasswordConfirmationException.class);
        verify(attempts).recordMfaSetupFailure(IP);
        verify(credentials, never()).save(any());
    }

    @Test
    @DisplayName("bloqueio por tentativas impede iniciar o cadastro")
    void bloqueioImpedeIniciar() {
        doThrow(new TooManyAttemptsException()).when(attempts).assertMfaSetupAllowed(IP);

        assertThatThrownBy(() -> service.start(user.id(), PASSWORD, IP, false))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(totp, never()).generateEnrollmentMaterial(any());
    }

    @Test
    @DisplayName("sessão comum não reinicia cadastro com MFA ativo: MfaAlreadyEnabledException")
    void sessaoComumNaoReiniciaCadastroAtivo() {
        enabled();

        assertThatThrownBy(() -> service.start(user.id(), PASSWORD, IP, false))
                .isInstanceOf(MfaAlreadyEnabledException.class);
    }

    @Test
    @DisplayName("código TOTP inválido mantém pendente e lança InvalidMfaChallengeException")
    void codigoInvalidoMantemPendente() {
        TotpCredential credential = pending();
        when(totp.verifyCode("SEGREDO", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(user.id(), "000000", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.PENDING);
        verify(attempts).recordMfaSetupFailure(IP);
        verify(sessions, never()).revokeAllForMfaChange(any());
    }

    @Test
    @DisplayName("confirmar sem cadastro pendente lança InvalidMfaChallengeException")
    void confirmarSemPendenteFalha() {
        when(credentials.findById(user.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(user.id(), "123456", IP))
                .isInstanceOf(InvalidMfaChallengeException.class);
    }

    @Test
    @DisplayName("desabilitar sem MFA ativo lança MfaNotEnabledException")
    void desabilitarSemMfaFalha() {
        pending();

        assertThatThrownBy(() -> service.disable(user.id(), PASSWORD))
                .isInstanceOf(MfaNotEnabledException.class);
        verify(sessions, never()).revokeAllForMfaChange(any());
    }

    @Test
    @DisplayName("desabilitar invalida os códigos de recuperação e revoga as sessões")
    void desabilitarInvalidaCodigos() {
        TotpCredential credential = enabled();

        service.disable(user.id(), PASSWORD);

        assertThat(credential.status()).isEqualTo(TotpCredentialStatus.DISABLED);
        verify(recoveryCodes).invalidateActiveForUserId(user.id(), NOW);
        verify(sessions).revokeAllForMfaChange(user.id());
    }

    @Test
    @DisplayName("usuário inexistente lança InvalidPasswordConfirmationException sem revelar a ausência")
    void usuarioInexistenteFalha() {
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerateRecoveryCodes(ghost, PASSWORD))
                .isInstanceOf(InvalidPasswordConfirmationException.class);
    }

    @Test
    @DisplayName("senha nula lança InvalidPasswordConfirmationException")
    void senhaNulaFalha() {
        enabled();

        assertThatThrownBy(() -> service.regenerateRecoveryCodes(user.id(), null))
                .isInstanceOf(InvalidPasswordConfirmationException.class);
        verify(recoveryCodes, never()).save(any());
    }
}
