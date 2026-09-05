package br.com.controlegastos.identity.application;

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
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaEnrollmentService {

    private final UserAccountRepository users;
    private final TotpCredentialRepository totpCredentials;
    private final RecoveryCodeRepository recoveryCodes;
    private final TotpSecretCipher cipher;
    private final TotpService totp;
    private final SessionService sessions;
    private final AuthAttemptService attempts;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();
    private final Clock clock;
    private final Duration pendingLifetime;

    public MfaEnrollmentService(
            UserAccountRepository users,
            TotpCredentialRepository totpCredentials,
            RecoveryCodeRepository recoveryCodes,
            TotpSecretCipher cipher,
            TotpService totp,
            SessionService sessions,
            AuthAttemptService attempts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.auth.mfa.pending-setup-lifetime}") Duration pendingLifetime
    ) {
        this.users = users;
        this.totpCredentials = totpCredentials;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.totp = totp;
        this.sessions = sessions;
        this.attempts = attempts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.pendingLifetime = pendingLifetime;
    }

    @Transactional(noRollbackFor = {InvalidPasswordConfirmationException.class, MfaAlreadyEnabledException.class})
    public EnrollmentStart start(UUID userId, String currentPassword, String remoteAddress) {
        attempts.assertMfaSetupAllowed(remoteAddress);
        try {
            EnrollmentStart result = doStart(userId, currentPassword);
            attempts.clearMfaSetupFailures(remoteAddress);
            return result;
        } catch (InvalidPasswordConfirmationException exception) {
            attempts.recordMfaSetupFailure(remoteAddress);
            throw exception;
        }
    }

    private EnrollmentStart doStart(UUID userId, String currentPassword) {
        UserAccount user = requirePassword(userId, currentPassword);
        Instant now = clock.instant();
        TotpCredential credential = totpCredentials.findById(userId)
                .orElseGet(() -> TotpCredential.initiallyDisabled(userId, now));
        if (credential.status() == TotpCredentialStatus.ENABLED) {
            throw new MfaAlreadyEnabledException();
        }
        TotpService.EnrollmentMaterial material = totp.generateEnrollmentMaterial(user.id());
        TotpSecretCipher.EncryptedSecret encrypted = cipher.encrypt(material.secret());
        credential.startEnrollment(encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), now, pendingLifetime);
        totpCredentials.save(credential);
        return new EnrollmentStart(material.otpauthUri(), material.qrImageDataUri(), material.secret(),
                credential.pendingExpiresAt());
    }

    @Transactional(noRollbackFor = InvalidMfaChallengeException.class)
    public List<String> confirm(UUID userId, String code, String remoteAddress) {
        attempts.assertMfaSetupAllowed(remoteAddress);
        try {
            List<String> codes = doConfirm(userId, code);
            attempts.clearMfaSetupFailures(remoteAddress);
            return codes;
        } catch (InvalidMfaChallengeException exception) {
            attempts.recordMfaSetupFailure(remoteAddress);
            throw exception;
        }
    }

    private List<String> doConfirm(UUID userId, String code) {
        Instant now = clock.instant();
        TotpCredential credential = totpCredentials.findById(userId)
                .filter(candidate -> candidate.canConfirmAt(now))
                .orElseThrow(InvalidMfaChallengeException::new);
        String secret = cipher.decrypt(credential.secretCiphertext(), credential.secretNonce(), credential.keyVersion());
        if (!totp.verifyCode(secret, code)) {
            throw new InvalidMfaChallengeException();
        }
        credential.confirm(now);
        totpCredentials.save(credential);
        List<String> rawCodes = reissueRecoveryCodes(userId, now);
        sessions.revokeAllForMfaChange(userId);
        return rawCodes;
    }

    @Transactional
    public void disable(UUID userId, String currentPassword) {
        requirePassword(userId, currentPassword);
        Instant now = clock.instant();
        TotpCredential credential = totpCredentials.findById(userId)
                .filter(candidate -> candidate.status() == TotpCredentialStatus.ENABLED)
                .orElseThrow(MfaNotEnabledException::new);
        credential.disable(now);
        totpCredentials.save(credential);
        recoveryCodes.invalidateActiveForUserId(userId, now);
        sessions.revokeAllForMfaChange(userId);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, String currentPassword) {
        requirePassword(userId, currentPassword);
        totpCredentials.findById(userId)
                .filter(candidate -> candidate.status() == TotpCredentialStatus.ENABLED)
                .orElseThrow(MfaNotEnabledException::new);
        return reissueRecoveryCodes(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public MfaStatus status(UUID userId) {
        TotpCredential credential = totpCredentials.findById(userId)
                .orElseGet(() -> TotpCredential.initiallyDisabled(userId, clock.instant()));
        return new MfaStatus(credential.status(), credential.pendingExpiresAt());
    }

    private List<String> reissueRecoveryCodes(UUID userId, Instant now) {
        recoveryCodes.invalidateActiveForUserId(userId, now);
        List<String> rawCodes = recoveryCodeGenerator.generate();
        rawCodes.forEach(rawCode ->
                recoveryCodes.save(RecoveryCode.issue(userId, Sha256.hex(rawCode), now)));
        return rawCodes;
    }

    private UserAccount requirePassword(UUID userId, String currentPassword) {
        UserAccount user = users.findById(userId).orElseThrow(InvalidPasswordConfirmationException::new);
        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.passwordCredential().passwordHash())) {
            throw new InvalidPasswordConfirmationException();
        }
        return user;
    }

    public record EnrollmentStart(String otpauthUri, String qrImageDataUri, String manualEntryKey,
                                   Instant pendingExpiresAt) {
    }

    public record MfaStatus(TotpCredentialStatus status, Instant pendingExpiresAt) {
    }
}
