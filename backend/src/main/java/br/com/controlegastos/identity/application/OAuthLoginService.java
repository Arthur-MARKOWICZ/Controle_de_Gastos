package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthAuthorizationState;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.IdentityProviderLinkRepository;
import br.com.controlegastos.identity.infrastructure.OAuthAuthorizationStateRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthLoginService {

    private final List<OAuthProviderClient> clients;
    private final OAuthAuthorizationStateRepository states;
    private final IdentityProviderLinkRepository links;
    private final UserAccountRepository users;
    private final TotpCredentialRepository totpCredentials;
    private final MfaLoginService mfaLogin;
    private final SessionService sessions;
    private final AuthAttemptService attempts;
    private final Clock clock;
    private final Duration stateLifetime;
    private final SecureRandom random = new SecureRandom();

    public OAuthLoginService(
            List<OAuthProviderClient> providerClients,
            OAuthAuthorizationStateRepository states,
            IdentityProviderLinkRepository links,
            UserAccountRepository users,
            TotpCredentialRepository totpCredentials,
            MfaLoginService mfaLogin,
            SessionService sessions,
            AuthAttemptService attempts,
            Clock clock,
            @Value("${app.oauth.state-lifetime}") Duration stateLifetime
    ) {
        this.clients = providerClients;
        this.states = states;
        this.links = links;
        this.users = users;
        this.totpCredentials = totpCredentials;
        this.mfaLogin = mfaLogin;
        this.sessions = sessions;
        this.attempts = attempts;
        this.clock = clock;
        this.stateLifetime = stateLifetime;
    }

    @Transactional
    public String buildAuthorizationUrl(OAuthProvider provider) {
        Instant now = clock.instant();
        String rawState = issueRawState();
        states.save(OAuthAuthorizationState.issue(Sha256.hex(rawState), provider, null, now, stateLifetime));
        return clientFor(provider).authorizationUrl(rawState);
    }

    @Transactional(noRollbackFor = OAuthLoginFailedException.class)
    public AuthenticationService.LoginOutcome completeCallback(
            OAuthProvider provider, String code, String rawState, String remoteAddress) {
        attempts.assertOAuthCallbackAllowed(remoteAddress);
        try {
            AuthenticationService.LoginOutcome outcome = doCompleteCallback(provider, code, rawState);
            attempts.clearOAuthCallbackFailures(remoteAddress);
            return outcome;
        } catch (OAuthLoginFailedException exception) {
            attempts.recordOAuthCallbackFailure(remoteAddress);
            throw exception;
        }
    }

    private AuthenticationService.LoginOutcome doCompleteCallback(OAuthProvider provider, String code, String rawState) {
        Instant now = clock.instant();
        OAuthAuthorizationState state = states.findLockedByStateHash(Sha256.hex(rawState))
                .filter(candidate -> candidate.canBeConsumedAt(now) && candidate.provider() == provider)
                .orElseThrow(OAuthLoginFailedException::new);
        state.consume(now);

        OAuthProviderClient client = clientFor(provider);
        String accessToken;
        OAuthProviderClient.ProviderProfile profile;
        try {
            accessToken = client.exchangeCode(code);
            profile = client.fetchProfile(accessToken);
        } catch (RuntimeException exception) {
            throw new OAuthLoginFailedException();
        }
        if (profile.email() == null) {
            throw new OAuthLoginFailedException();
        }
        EmailAddress email;
        try {
            email = EmailAddress.from(profile.email());
        } catch (IllegalArgumentException exception) {
            throw new OAuthLoginFailedException();
        }

        UUID userId = resolveUserId(provider, profile.providerUserId(), email, now);
        boolean requiresMfa = totpCredentials.findById(userId).map(TotpCredential::requiresMfaAtLogin).orElse(false);
        if (requiresMfa) {
            return new AuthenticationService.LoginOutcome(null, mfaLogin.createChallenge(userId));
        }
        return new AuthenticationService.LoginOutcome(sessions.start(userId), null);
    }

    private UUID resolveUserId(OAuthProvider provider, String providerUserId, EmailAddress email, Instant now) {
        return links.findByProviderAndProviderUserId(provider, providerUserId)
                .map(IdentityProviderLink::userId)
                .orElseGet(() -> {
                    if (users.findByEmailNormalized(email.value()).isPresent()) {
                        throw new OAuthLoginFailedException();
                    }
                    UserAccount user = UserAccount.registerWithProvider(email, now);
                    users.save(user);
                    totpCredentials.save(TotpCredential.initiallyDisabled(user.id(), now));
                    links.save(IdentityProviderLink.link(user.id(), provider, providerUserId, email.value(), now));
                    return user.id();
                });
    }

    private OAuthProviderClient clientFor(OAuthProvider provider) {
        return clients.stream()
                .filter(client -> client.provider() == provider)
                .findFirst()
                .orElseThrow(OAuthLoginFailedException::new);
    }

    private String issueRawState() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
