package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.application.SessionRepository;
import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.identity.infrastructure.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityRetentionService {

    private static final Duration SESSION_FORENSIC_WINDOW = Duration.ofDays(30);
    private final AuthAttemptRepository attempts;
    private final SessionRepository sessions;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final Clock clock;

    public IdentityRetentionService(AuthAttemptRepository attempts, SessionRepository sessions,
                                    PasswordResetTokenRepository passwordResetTokens, Clock clock) {
        this.attempts = attempts;
        this.sessions = sessions;
        this.passwordResetTokens = passwordResetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    @Transactional
    public void discardExpiredTechnicalData() {
        var now = clock.instant();
        attempts.deleteExpired(now);
        sessions.deleteEndedBefore(now.minus(SESSION_FORENSIC_WINDOW));
        passwordResetTokens.deleteEndedBefore(now.minus(Duration.ofHours(24)));
    }
}
