package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.domain.UserStatus;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface
public class AuthenticationService {

    private static final String DUMMY_PASSWORD = "dummy password never authenticates";
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;

    private final UserAccountRepository users;
    private final SessionService sessions;
    private final AuthAttemptService attempts;
    private final TotpCredentialRepository totpCredentials;
    private final MfaLoginService mfaLogin;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticationService(
            UserAccountRepository users,
            SessionService sessions,
            AuthAttemptService attempts,
            TotpCredentialRepository totpCredentials,
            MfaLoginService mfaLogin,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.users = users;
        this.sessions = sessions;
        this.attempts = attempts;
        this.totpCredentials = totpCredentials;
        this.mfaLogin = mfaLogin;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional
    public void register(String rawEmail, String password, String remoteAddress) {
        if (!attempts.consumeRegistrationAttempt(remoteAddress)) {
            return;
        }
        EmailAddress email = EmailAddress.from(rawEmail);
        validatePassword(email, password);
        String passwordHash = passwordEncoder.encode(password);
        users.lockNormalizedEmail(email.value());
        if (users.findByEmailNormalized(email.value()).isPresent()) {
            return;
        }
        UserAccount user = UserAccount.register(email, passwordHash, clock.instant());
        users.save(user);
        totpCredentials.save(TotpCredential.initiallyDisabled(user.id(), clock.instant()));
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginOutcome login(String rawEmail, String password, String remoteAddress) {
        attempts.assertLoginAllowed(rawEmail, remoteAddress);
        try {
            LoginOutcome outcome = authenticate(rawEmail, password);
            attempts.clearLoginFailures(rawEmail, remoteAddress);
            return outcome;
        } catch (InvalidCredentialsException exception) {
            attempts.recordLoginFailure(rawEmail, remoteAddress);
            throw exception;
        }
    }

    private LoginOutcome authenticate(String rawEmail, String password) {
        EmailAddress email;
        try {
            email = EmailAddress.from(rawEmail);
        } catch (IllegalArgumentException exception) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        UserAccount user = users.findByEmailNormalized(email.value()).orElse(null);
        String storedHash = (user == null || !user.hasPassword())
                ? dummyPasswordHash
                : user.passwordCredential().passwordHash();
        boolean passwordMatches = password != null && passwordEncoder.matches(password, storedHash);
        if (user == null || user.status() != UserStatus.ACTIVE || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        boolean requiresMfa = totpCredentials.findById(user.id())
                .map(TotpCredential::requiresMfaAtLogin)
                .orElse(false);
        if (requiresMfa) {
            return new LoginOutcome(null, mfaLogin.createChallenge(user.id()));
        }
        return new LoginOutcome(sessions.start(user.id()), null);
    }

    public SessionService.AuthenticatedSession refresh(String rawRefreshToken) {
        return sessions.refresh(rawRefreshToken);
    }

    public void logout() {
        sessions.logout(currentSessionId());
    }

    public UUID currentUserId() {
        return UUID.fromString(currentAuthentication().getToken().getSubject());
    }

    public UUID currentSessionId() {
        return UUID.fromString(currentAuthentication().getToken().getClaimAsString("sid"));
    }

    public boolean isRestrictedMfaSession() {
        return currentAuthentication().getToken().getClaimAsString("mfa_scope") != null;
    }

    static void validatePassword(EmailAddress email, String password) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH
                || password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("A senha deve ter entre 12 e 128 caracteres");
        }
        if (password.equalsIgnoreCase(email.value())) {
            throw new IllegalArgumentException("A senha não pode ser igual ao e-mail");
        }
    }

    private JwtAuthenticationToken currentAuthentication() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        return jwt;
    }

    public record LoginOutcome(SessionService.AuthenticatedSession session, MfaLoginService.ChallengeIssued challenge) {
        public boolean requiresMfa() {
            return challenge != null;
        }
    }
}
