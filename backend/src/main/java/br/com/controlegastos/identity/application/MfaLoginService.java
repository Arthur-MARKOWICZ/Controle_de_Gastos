package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.MfaLoginChallenge;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.TotpCredentialStatus;
import br.com.controlegastos.identity.infrastructure.MfaLoginChallengeRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.TotpSecretCipher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaLoginService {

    private final MfaLoginChallengeRepository challenges;
    private final TotpCredentialRepository totpCredentials;
    private final TotpSecretCipher cipher;
    private final TotpService totp;
    private final SessionService sessions;
    private final AuthAttemptService attempts;
    private final Clock clock;
    private final Duration challengeLifetime;
    private final SecureRandom random = new SecureRandom();

    public MfaLoginService(
            MfaLoginChallengeRepository challenges,
            TotpCredentialRepository totpCredentials,
            TotpSecretCipher cipher,
            TotpService totp,
            SessionService sessions,
            AuthAttemptService attempts,
            Clock clock,
            @Value("${app.auth.mfa.login-challenge-lifetime}") Duration challengeLifetime
    ) {
        this.challenges = challenges;
        this.totpCredentials = totpCredentials;
        this.cipher = cipher;
        this.totp = totp;
        this.sessions = sessions;
        this.attempts = attempts;
        this.clock = clock;
        this.challengeLifetime = challengeLifetime;
    }

    @Transactional
    public ChallengeIssued createChallenge(UUID userId) {
        Instant now = clock.instant();
        String rawChallenge = issueRawChallenge();
        challenges.save(MfaLoginChallenge.issue(userId, Sha256.hex(rawChallenge), now, challengeLifetime));
        return new ChallengeIssued(rawChallenge, challengeLifetime.toSeconds());
    }

    @Transactional(noRollbackFor = InvalidMfaChallengeException.class)
    public SessionService.AuthenticatedSession verify(String rawChallengeId, String code, String remoteAddress) {
        attempts.assertMfaVerifyAllowed(remoteAddress);
        try {
            SessionService.AuthenticatedSession session = doVerify(rawChallengeId, code);
            attempts.clearMfaVerifyFailures(remoteAddress);
            return session;
        } catch (InvalidMfaChallengeException exception) {
            attempts.recordMfaVerifyFailure(remoteAddress);
            throw exception;
        }
    }

    private SessionService.AuthenticatedSession doVerify(String rawChallengeId, String code) {
        Instant now = clock.instant();
        MfaLoginChallenge challenge = challenges.findLockedByChallengeHash(Sha256.hex(rawChallengeId))
                .filter(candidate -> candidate.canBeConsumedAt(now))
                .orElseThrow(InvalidMfaChallengeException::new);
        TotpCredential credential = totpCredentials.findById(challenge.userId())
                .filter(candidate -> candidate.status() == TotpCredentialStatus.ENABLED)
                .orElseThrow(InvalidMfaChallengeException::new);
        String secret = cipher.decrypt(credential.secretCiphertext(), credential.secretNonce(), credential.keyVersion());
        if (!totp.verifyCode(secret, code)) {
            throw new InvalidMfaChallengeException();
        }
        challenge.consume(now);
        return sessions.start(challenge.userId());
    }

    private String issueRawChallenge() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record ChallengeIssued(String challengeId, long expiresIn) {
    }
}
