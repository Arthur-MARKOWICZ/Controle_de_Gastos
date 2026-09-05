package br.com.controlegastos.identity.application;

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
}
