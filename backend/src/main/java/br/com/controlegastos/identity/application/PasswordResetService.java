package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.PasswordResetToken;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.domain.UserStatus;
import br.com.controlegastos.identity.infrastructure.PasswordResetTokenRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PasswordResetService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final Duration TOKEN_RETENTION = Duration.ofHours(24);
    private final UserAccountRepository users;
    private final PasswordResetTokenRepository tokens;
    private final AuthAttemptService attempts;
    private final SessionService sessions;
    private final PasswordResetMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String publicUrl;

    public PasswordResetService(UserAccountRepository users, PasswordResetTokenRepository tokens,
                                AuthAttemptService attempts, SessionService sessions,
                                PasswordResetMailSender mailSender, PasswordEncoder passwordEncoder, Clock clock,
                                @Value("${app.auth.password-reset.public-url:}") String publicUrl) {
        this.users = users;
        this.tokens = tokens;
        this.attempts = attempts;
        this.sessions = sessions;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.publicUrl = publicUrl;
    }

    @Transactional
    public void request(String rawEmail, String remoteAddress) {
        if (!attempts.consumePasswordResetAttempt(rawEmail, remoteAddress)) return;
        EmailAddress email;
        try { email = EmailAddress.from(rawEmail); } catch (RuntimeException exception) { return; }
        UserAccount user = users.findByEmailNormalized(email.value()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) return;
        Instant now = clock.instant();
        tokens.invalidateActiveForUserId(user.id(), now);
        tokens.deleteEndedBefore(now.minus(TOKEN_RETENTION));
        String rawToken = issueRawToken();
        tokens.save(PasswordResetToken.issue(user.id(), hash(rawToken), now, TOKEN_LIFETIME));
        afterCommit(() -> safelySend(() -> mailSender.sendPasswordReset(user.emailNormalized(), resetUrl(rawToken))));
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findLockedByTokenHash(hash(rawToken))
                .filter(candidate -> candidate.canBeConsumedAt(now))
                .orElseThrow(InvalidPasswordResetTokenException::new);
        UserAccount user = users.findById(token.userId()).filter(candidate -> candidate.status() == UserStatus.ACTIVE)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        AuthenticationService.validatePassword(new EmailAddress(user.emailNormalized()), newPassword);
        user.changePasswordAndVerifyEmail(passwordEncoder.encode(newPassword), now);
        token.consume(now);
        sessions.revokeAllForPasswordReset(user.id());
        afterCommit(() -> safelySend(() -> mailSender.sendPasswordChanged(user.emailNormalized())));
    }

    private String resetUrl(String rawToken) {
        if (!publicUrl.startsWith("https://")) throw new IllegalStateException("AUTH_PUBLIC_APP_URL deve iniciar com https://");
        return publicUrl.replaceAll("/+$", "") + "/redefinir-senha#token=" + rawToken;
    }

    private String issueRawToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String rawToken) {
        return Sha256.hex(rawToken);
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private static void safelySend(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOG.error("Falha ao enviar e-mail de recuperação de senha");
        }
    }
}
