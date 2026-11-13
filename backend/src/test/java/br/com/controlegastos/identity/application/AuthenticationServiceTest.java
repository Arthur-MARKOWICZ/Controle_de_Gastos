package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticationServiceTest {

    private static final String EMAIL = "usuario@example.com";
    private static final String PASSWORD = "frase segura de teste";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTheAuthenticatedUserAndSessionFromTheJwt() {
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID sessionId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        AuthenticationService service = new AuthenticationService(
                Mockito.mock(UserAccountRepository.class),
                Mockito.mock(SessionService.class),
                Mockito.mock(AuthAttemptService.class),
                Mockito.mock(TotpCredentialRepository.class),
                Mockito.mock(MfaLoginService.class),
                Mockito.mock(PasswordEncoder.class),
                Clock.systemUTC()
        );

        assertThat(service.currentUserId()).isEqualTo(userId);
        assertThat(service.currentSessionId()).isEqualTo(sessionId);
    }

    @Test
    void loginForAUserWithoutMfaStartsANormalSession() {
        UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
        SessionService sessions = Mockito.mock(SessionService.class);
        TotpCredentialRepository totpCredentials = Mockito.mock(TotpCredentialRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        UserAccount user = UserAccount.register(EmailAddress.from(EMAIL), "hash", Instant.now());
        SessionService.AuthenticatedSession session =
                new SessionService.AuthenticatedSession("token", 900, "refresh");

        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(totpCredentials.findById(user.id()))
                .thenReturn(Optional.of(TotpCredential.initiallyDisabled(user.id(), Instant.now())));
        when(sessions.start(user.id())).thenReturn(session);

        AuthenticationService service = new AuthenticationService(
                users, sessions, Mockito.mock(AuthAttemptService.class), totpCredentials,
                Mockito.mock(MfaLoginService.class), passwordEncoder, Clock.systemUTC()
        );

        AuthenticationService.LoginOutcome outcome = service.login(EMAIL, PASSWORD, "127.0.0.1");

        assertThat(outcome.requiresMfa()).isFalse();
        assertThat(outcome.session()).isEqualTo(session);
    }

    @Test
    void loginForAUserWithMfaEnabledReturnsAChallengeAndNeverStartsASession() {
        UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
        SessionService sessions = Mockito.mock(SessionService.class);
        TotpCredentialRepository totpCredentials = Mockito.mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = Mockito.mock(MfaLoginService.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        UserAccount user = UserAccount.register(EmailAddress.from(EMAIL), "hash", Instant.now());
        TotpCredential enabled = TotpCredential.initiallyDisabled(user.id(), Instant.now());
        enabled.startEnrollment("cipher".getBytes(), "nonce".getBytes(), 1, Instant.now(), java.time.Duration.ofMinutes(10));
        enabled.confirm(Instant.now());
        MfaLoginService.ChallengeIssued challenge = new MfaLoginService.ChallengeIssued("challenge-id", 300);

        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(totpCredentials.findById(user.id())).thenReturn(Optional.of(enabled));
        when(mfaLogin.createChallenge(user.id())).thenReturn(challenge);

        AuthenticationService service = new AuthenticationService(
                users, sessions, Mockito.mock(AuthAttemptService.class), totpCredentials,
                mfaLogin, passwordEncoder, Clock.systemUTC()
        );

        AuthenticationService.LoginOutcome outcome = service.login(EMAIL, PASSWORD, "127.0.0.1");

        assertThat(outcome.requiresMfa()).isTrue();
        assertThat(outcome.challenge()).isEqualTo(challenge);
        verify(sessions, never()).start(any());
    }

    private static final String IP = "203.0.113.30";
    private final UserAccountRepository users = Mockito.mock(UserAccountRepository.class);
    private final SessionService sessionService = Mockito.mock(SessionService.class);
    private final AuthAttemptService attempts = Mockito.mock(AuthAttemptService.class);
    private final TotpCredentialRepository totp = Mockito.mock(TotpCredentialRepository.class);
    private final PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);

    private AuthenticationService service() {
        when(encoder.encode("dummy password never authenticates")).thenReturn("dummy-hash");
        return new AuthenticationService(users, sessionService, attempts, totp,
                Mockito.mock(MfaLoginService.class), encoder, Clock.systemUTC());
    }

    @Test
    @DisplayName("senha errada registra falha e lança InvalidCredentialsException")
    void senhaErradaRegistraFalha() {
        AuthenticationService service = service();
        UserAccount user = UserAccount.register(EmailAddress.from(EMAIL), "hash", Instant.now());
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(user));
        when(encoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "errada", IP)).isInstanceOf(InvalidCredentialsException.class);
        verify(attempts).recordLoginFailure(EMAIL, IP);
        verify(sessionService, never()).start(any());
    }

    @Test
    @DisplayName("e-mail sem conta compara com hash fictício para não vazar a existência pelo tempo")
    void emailSemContaUsaHashFicticio() {
        AuthenticationService service = service();
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, IP)).isInstanceOf(InvalidCredentialsException.class);
        verify(encoder).matches(PASSWORD, "dummy-hash");
    }

    @Test
    @DisplayName("conta bloqueada não entra mesmo com senha correta")
    void contaBloqueadaNaoEntra() {
        AuthenticationService service = service();
        UserAccount user = UserAccount.register(EmailAddress.from(EMAIL), "hash", Instant.now());
        user.block(Instant.now());
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(user));
        when(encoder.matches(PASSWORD, "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, IP)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("bloqueio por tentativas lança TooManyAttemptsException antes de verificar a senha")
    void bloqueioPorTentativas() {
        AuthenticationService service = service();
        Mockito.doThrow(new TooManyAttemptsException()).when(attempts).assertLoginAllowed(EMAIL, IP);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, IP)).isInstanceOf(TooManyAttemptsException.class);
        verify(encoder, never()).matches(PASSWORD, "hash");
    }

    @Test
    @DisplayName("cadastro de e-mail já existente não cria conta nem revela a duplicidade")
    void cadastroDuplicadoSilencioso() {
        AuthenticationService service = service();
        when(attempts.consumeRegistrationAttempt(IP)).thenReturn(true);
        when(users.findByEmailNormalized(EMAIL))
                .thenReturn(Optional.of(UserAccount.register(EmailAddress.from(EMAIL), "hash", Instant.now())));

        service.register(EMAIL, PASSWORD, IP);

        verify(users).lockNormalizedEmail(EMAIL);
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("cadastro com senha de 11 caracteres lança IllegalArgumentException")
    void cadastroSenhaCurtaFalha() {
        AuthenticationService service = service();
        when(attempts.consumeRegistrationAttempt(IP)).thenReturn(true);

        assertThatThrownBy(() -> service.register(EMAIL, "a".repeat(11), IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A senha deve ter entre 12 e 128 caracteres");
    }

    @Test
    @DisplayName("cadastro com senha de 129 caracteres lança IllegalArgumentException")
    void cadastroSenhaLongaFalha() {
        AuthenticationService service = service();
        when(attempts.consumeRegistrationAttempt(IP)).thenReturn(true);

        assertThatThrownBy(() -> service.register(EMAIL, "a".repeat(129), IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A senha deve ter entre 12 e 128 caracteres");
    }

    @Test
    @DisplayName("sem autenticação no contexto, ler o usuário atual lança IllegalStateException")
    void semAutenticacaoFalha() {
        assertThatThrownBy(() -> service().currentUserId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Usuário não autenticado");
        assertThat(service().currentUserIdOrNull()).isNull();
    }
}
