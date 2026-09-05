package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.application.TooManyAttemptsException;
import br.com.controlegastos.identity.domain.AuthAttempt;
import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAttemptService {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int LIMIT = 5;

    private final AuthAttemptRepository attempts;
    private final Clock clock;
    private final byte[] hmacSecret;

    public AuthAttemptService(
            AuthAttemptRepository attempts,
            Clock clock,
            @Value("${app.auth.attempt-hmac-secret}") String attemptHmacSecret
    ) {
        this.attempts = attempts;
        this.clock = clock;
        this.hmacSecret = attemptHmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public void assertLoginAllowed(String rawEmail, String remoteAddress) {
        String key = key("LOGIN", rawEmail, remoteAddress);
        if (attempts.findById(key).map(attempt -> attempt.isBlockedAt(clock.instant())).orElse(false)) {
            throw new TooManyAttemptsException();
        }
    }

    @Transactional
    public void recordLoginFailure(String rawEmail, String remoteAddress) {
        String key = key("LOGIN", rawEmail, remoteAddress);
        attempts.lockKey(key);
        Instant now = clock.instant();
        attempts.deleteExpired(now);
        AuthAttempt attempt = attempts.findById(key).orElseGet(() -> new AuthAttempt(key, now, RETENTION));
        attempt.recordFailure(now, WINDOW, BLOCK_DURATION, RETENTION, LIMIT);
        attempts.save(attempt);
    }

    @Transactional
    public void clearLoginFailures(String rawEmail, String remoteAddress) {
        String key = key("LOGIN", rawEmail, remoteAddress);
        attempts.lockKey(key);
        attempts.deleteById(key);
    }

    @Transactional
    public boolean consumeRegistrationAttempt(String remoteAddress) {
        String key = key("REGISTER", "*", remoteAddress);
        attempts.lockKey(key);
        Instant now = clock.instant();
        attempts.deleteExpired(now);
        AuthAttempt attempt = attempts.findById(key).orElseGet(() -> new AuthAttempt(key, now, RETENTION));
        if (attempt.isBlockedAt(now)) {
            return false;
        }
        attempt.recordFailure(now, WINDOW, BLOCK_DURATION, RETENTION, LIMIT);
        attempts.save(attempt);
        return true;
    }

    @Transactional
    public boolean consumePasswordResetAttempt(String rawEmail, String remoteAddress) {
        String key = key("PASSWORD_RESET", rawEmail, remoteAddress);
        attempts.lockKey(key);
        Instant now = clock.instant();
        attempts.deleteExpired(now);
        AuthAttempt attempt = attempts.findById(key).orElseGet(() -> new AuthAttempt(key, now, RETENTION));
        if (attempt.isBlockedAt(now)) return false;
        attempt.recordFailure(now, WINDOW, BLOCK_DURATION, RETENTION, LIMIT);
        attempts.save(attempt);
        return true;
    }

    @Transactional(readOnly = true)
    public void assertMfaVerifyAllowed(String remoteAddress) {
        String key = key("MFA_VERIFY", "*", remoteAddress);
        if (attempts.findById(key).map(attempt -> attempt.isBlockedAt(clock.instant())).orElse(false)) {
            throw new TooManyAttemptsException();
        }
    }

    @Transactional
    public void recordMfaVerifyFailure(String remoteAddress) {
        String key = key("MFA_VERIFY", "*", remoteAddress);
        attempts.lockKey(key);
        Instant now = clock.instant();
        attempts.deleteExpired(now);
        AuthAttempt attempt = attempts.findById(key).orElseGet(() -> new AuthAttempt(key, now, RETENTION));
        attempt.recordFailure(now, WINDOW, BLOCK_DURATION, RETENTION, LIMIT);
        attempts.save(attempt);
    }

    @Transactional
    public void clearMfaVerifyFailures(String remoteAddress) {
        String key = key("MFA_VERIFY", "*", remoteAddress);
        attempts.lockKey(key);
        attempts.deleteById(key);
    }

    @Transactional(readOnly = true)
    public void assertMfaRecoveryAllowed(String remoteAddress) {
        String key = key("MFA_RECOVERY", "*", remoteAddress);
        if (attempts.findById(key).map(attempt -> attempt.isBlockedAt(clock.instant())).orElse(false)) {
            throw new TooManyAttemptsException();
        }
    }

    @Transactional
    public void recordMfaRecoveryFailure(String remoteAddress) {
        String key = key("MFA_RECOVERY", "*", remoteAddress);
        attempts.lockKey(key);
        Instant now = clock.instant();
        attempts.deleteExpired(now);
        AuthAttempt attempt = attempts.findById(key).orElseGet(() -> new AuthAttempt(key, now, RETENTION));
        attempt.recordFailure(now, WINDOW, BLOCK_DURATION, RETENTION, LIMIT);
        attempts.save(attempt);
    }

    @Transactional
    public void clearMfaRecoveryFailures(String remoteAddress) {
        String key = key("MFA_RECOVERY", "*", remoteAddress);
        attempts.lockKey(key);
        attempts.deleteById(key);
    }

    @Transactional(readOnly = true)
    public void assertMfaSetupAllowed(String remoteAddress) {
        String key = key("MFA_SETUP", "*", remoteAddress);
        if (attempts.findById(key).map(attempt -> attempt.isBlockedAt(clock.instant())).orElse(false)) {
            throw new TooManyAttemptsException();
        }
    }

    @Transactional
    public void recordMfaSetupFailure(String remoteAddress) {
        String key = key("MFA_SETUP", "*", remoteAddress);
        attempts.lockKey(key);
        Instant now = clock.instant();
        attempts.deleteExpired(now);
        AuthAttempt attempt = attempts.findById(key).orElseGet(() -> new AuthAttempt(key, now, RETENTION));
        attempt.recordFailure(now, WINDOW, BLOCK_DURATION, RETENTION, LIMIT);
        attempts.save(attempt);
    }

    @Transactional
    public void clearMfaSetupFailures(String remoteAddress) {
        String key = key("MFA_SETUP", "*", remoteAddress);
        attempts.lockKey(key);
        attempts.deleteById(key);
    }

    private String key(String scope, String rawEmail, String remoteAddress) {
        String normalizedEmail;
        try {
            normalizedEmail = EmailAddress.from(rawEmail).value();
        } catch (RuntimeException exception) {
            normalizedEmail = "invalid";
        }
        String material = scope + '\n' + normalizedEmail + '\n' + String.valueOf(remoteAddress);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC indisponível", exception);
        }
    }
}
