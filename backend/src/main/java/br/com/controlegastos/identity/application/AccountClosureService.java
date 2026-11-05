package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.domain.UserStatus;
import br.com.controlegastos.identity.infrastructure.IdentityProviderLinkRepository;
import br.com.controlegastos.identity.infrastructure.MfaLoginChallengeRepository;
import br.com.controlegastos.identity.infrastructure.OAuthAuthorizationStateRepository;
import br.com.controlegastos.identity.infrastructure.OAuthMobileHandoffRepository;
import br.com.controlegastos.identity.infrastructure.PasswordResetTokenRepository;
import br.com.controlegastos.identity.infrastructure.RecoveryCodeRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface
public class AccountClosureService {

    private final UserAccountRepository users;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final TotpCredentialRepository totpCredentials;
    private final RecoveryCodeRepository recoveryCodes;
    private final MfaLoginChallengeRepository mfaChallenges;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final IdentityProviderLinkRepository providerLinks;
    private final OAuthAuthorizationStateRepository oauthStates;
    private final OAuthMobileHandoffRepository mobileHandoffs;
    private final Clock clock;

    public AccountClosureService(
            UserAccountRepository users,
            SessionService sessions,
            PasswordEncoder passwordEncoder,
            TotpCredentialRepository totpCredentials,
            RecoveryCodeRepository recoveryCodes,
            MfaLoginChallengeRepository mfaChallenges,
            PasswordResetTokenRepository passwordResetTokens,
            IdentityProviderLinkRepository providerLinks,
            OAuthAuthorizationStateRepository oauthStates,
            OAuthMobileHandoffRepository mobileHandoffs,
            Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.totpCredentials = totpCredentials;
        this.recoveryCodes = recoveryCodes;
        this.mfaChallenges = mfaChallenges;
        this.passwordResetTokens = passwordResetTokens;
        this.providerLinks = providerLinks;
        this.oauthStates = oauthStates;
        this.mobileHandoffs = mobileHandoffs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportIdentity(UUID userId) {
        UserAccount user = requireActive(userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.id().toString());
        payload.put("email", user.emailNormalized());
        payload.put("status", user.status().name());
        payload.put("createdAt", user.createdAt().toString());
        payload.put("emailVerifiedAt", user.emailVerifiedAt() == null ? null : user.emailVerifiedAt().toString());
        payload.put("hasPassword", user.hasPassword());
        payload.put("linkedProviders", providerLinks.findByUserId(userId).stream()
                .map(link -> link.provider().name())
                .toList());
        return payload;
    }

    @Transactional
    public void closeAccount(UUID userId, String password) {
        UserAccount user = requireActive(userId);
        if (!user.hasPassword() || password == null
                || !passwordEncoder.matches(password, user.passwordCredential().passwordHash())) {
            throw new InvalidCredentialsException();
        }
        Instant now = clock.instant();
        sessions.revokeAllForAccountClosure(userId);
        passwordResetTokens.invalidateActiveForUserId(userId, now);
        recoveryCodes.invalidateActiveForUserId(userId, now);
        totpCredentials.findById(userId).ifPresent(totpCredentials::delete);
        mfaChallenges.deleteByUserId(userId);
        providerLinks.deleteByUserId(userId);
        oauthStates.deleteByLinkingUserId(userId);
        mobileHandoffs.deleteByUserId(userId);
        user.closeAsDeleted(now);
        users.save(user);
    }

    private UserAccount requireActive(UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (user.status() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
