package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.MfaLoginChallenge;
import br.com.controlegastos.identity.domain.RecoveryCode;
import br.com.controlegastos.identity.infrastructure.MfaLoginChallengeRepository;
import br.com.controlegastos.identity.infrastructure.RecoveryCodeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryLoginService {

    private final MfaLoginChallengeRepository challenges;
    private final RecoveryCodeRepository recoveryCodes;
    private final RestrictedSessionTokenService restrictedSessions;
    private final AuthAttemptService attempts;
    private final Clock clock;

    public RecoveryLoginService(
            MfaLoginChallengeRepository challenges,
            RecoveryCodeRepository recoveryCodes,
            RestrictedSessionTokenService restrictedSessions,
            AuthAttemptService attempts,
            Clock clock
    ) {
        this.challenges = challenges;
        this.recoveryCodes = recoveryCodes;
        this.restrictedSessions = restrictedSessions;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = InvalidRecoveryCodeException.class)
    public RestrictedSessionTokenService.RestrictedToken verify(
            String rawChallengeId, String rawRecoveryCode, String remoteAddress) {
        attempts.assertMfaRecoveryAllowed(remoteAddress);
        try {
            RestrictedSessionTokenService.RestrictedToken token = doVerify(rawChallengeId, rawRecoveryCode);
            attempts.clearMfaRecoveryFailures(remoteAddress);
            return token;
        } catch (InvalidRecoveryCodeException exception) {
            attempts.recordMfaRecoveryFailure(remoteAddress);
            throw exception;
        }
    }

    private RestrictedSessionTokenService.RestrictedToken doVerify(String rawChallengeId, String rawRecoveryCode) {
        Instant now = clock.instant();
        MfaLoginChallenge challenge = challenges.findLockedByChallengeHash(Sha256.hex(rawChallengeId))
                .filter(candidate -> candidate.canBeConsumedAt(now))
                .orElseThrow(InvalidRecoveryCodeException::new);
        RecoveryCode recoveryCode = recoveryCodes
                .findLockedByUserIdAndCodeHash(challenge.userId(), Sha256.hex(normalize(rawRecoveryCode)))
                .filter(candidate -> candidate.canBeConsumedAt(now))
                .orElseThrow(InvalidRecoveryCodeException::new);
        recoveryCode.consume(now);
        challenge.consume(now);
        return restrictedSessions.issueRecoverySetupToken(challenge.userId());
    }

    private String normalize(String rawRecoveryCode) {
        return rawRecoveryCode == null ? "" : rawRecoveryCode.trim().toUpperCase(Locale.ROOT);
    }
}
