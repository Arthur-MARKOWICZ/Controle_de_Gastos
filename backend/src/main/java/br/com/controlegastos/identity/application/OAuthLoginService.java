package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthAuthorizationState;
import br.com.controlegastos.identity.domain.OAuthClientKind;
import br.com.controlegastos.identity.domain.OAuthMobileHandoff;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.domain.TotpCredential;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.IdentityProviderLinkRepository;
import br.com.controlegastos.identity.infrastructure.OAuthAuthorizationStateRepository;
import br.com.controlegastos.identity.infrastructure.OAuthMobileHandoffRepository;
import br.com.controlegastos.identity.infrastructure.TotpCredentialRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthLoginService {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthLoginService.class);

    private final List<OAuthProviderClient> clients;
    private final OAuthAuthorizationStateRepository states;
    private final OAuthMobileHandoffRepository mobileHandoffs;
    private final IdentityProviderLinkRepository links;
    private final UserAccountRepository users;
    private final TotpCredentialRepository totpCredentials;
    private final MfaLoginService mfaLogin;
    private final SessionService sessions;
    private final AuthAttemptService attempts;
    private final Clock clock;
    private final Duration stateLifetime;
    private final Duration mobileHandoffLifetime;
    private final SecureRandom random = new SecureRandom();

    public OAuthLoginService(
            List<OAuthProviderClient> providerClients,
            OAuthAuthorizationStateRepository states,
            OAuthMobileHandoffRepository mobileHandoffs,
            IdentityProviderLinkRepository links,
            UserAccountRepository users,
            TotpCredentialRepository totpCredentials,
            MfaLoginService mfaLogin,
            SessionService sessions,
            AuthAttemptService attempts,
            Clock clock,
            @Value("${app.oauth.state-lifetime}") Duration stateLifetime,
            @Value("${app.oauth.mobile-handoff-lifetime}") Duration mobileHandoffLifetime
    ) {
        this.clients = providerClients;
        this.states = states;
        this.mobileHandoffs = mobileHandoffs;
        this.links = links;
        this.users = users;
        this.totpCredentials = totpCredentials;
        this.mfaLogin = mfaLogin;
        this.sessions = sessions;
        this.attempts = attempts;
        this.clock = clock;
        this.stateLifetime = stateLifetime;
        this.mobileHandoffLifetime = mobileHandoffLifetime;
    }

    @Transactional
    public String buildAuthorizationUrl(OAuthProvider provider, UUID linkingUserId) {
        return buildAuthorizationUrl(provider, linkingUserId, OAuthClientKind.WEB);
    }

    @Transactional
    public String buildAuthorizationUrl(OAuthProvider provider, UUID linkingUserId, OAuthClientKind client) {
        Instant now = clock.instant();
        String rawState = issueRawState();
        states.save(OAuthAuthorizationState.issue(
                Sha256.hex(rawState), provider, linkingUserId, client, now, stateLifetime));
        return clientFor(provider).authorizationUrl(rawState);
    }

    /**
     * Troca o código de handoff pela sessão, uma única vez.
     *
     * <p>O aplicativo nativo recebe o código pelo App Link e o apresenta aqui.
     * Os tokens voltam no corpo, não em cookie, porque o navegador do sistema
     * não compartilha cookies com o aplicativo. Ver ADR-020.
     */
    @Transactional
    public SessionService.AuthenticatedSession redeemMobileHandoff(String rawCode, String remoteAddress) {
        attempts.assertOAuthCallbackAllowed(remoteAddress);
        Instant now = clock.instant();
        OAuthMobileHandoff handoff = mobileHandoffs.findLockedByCodeHash(Sha256.hex(rawCode))
                .filter(candidate -> candidate.canBeConsumedAt(now))
                .orElseThrow(() -> {
                    attempts.recordOAuthCallbackFailure(remoteAddress);
                    LOG.warn("Falha no handoff móvel: código ausente, expirado ou já consumido");
                    return new OAuthLoginFailedException();
                });
        handoff.consume(now);
        attempts.clearOAuthCallbackFailures(remoteAddress);
        return sessions.start(handoff.userId());
    }

    /** Emite o código de uso único entregue ao aplicativo pelo App Link. */
    private String issueMobileHandoff(UUID userId, Instant now) {
        String rawCode = issueRawState();
        mobileHandoffs.save(OAuthMobileHandoff.issue(userId, Sha256.hex(rawCode), now, mobileHandoffLifetime));
        return rawCode;
    }

    @Transactional(noRollbackFor = OAuthLoginFailedException.class)
    public OAuthCallbackOutcome completeCallback(
            OAuthProvider provider, String code, String rawState, String remoteAddress) {
        attempts.assertOAuthCallbackAllowed(remoteAddress);
        try {
            OAuthCallbackOutcome outcome = doCompleteCallback(provider, code, rawState);
            attempts.clearOAuthCallbackFailures(remoteAddress);
            return outcome;
        } catch (OAuthLoginFailedException exception) {
            attempts.recordOAuthCallbackFailure(remoteAddress);
            throw exception;
        }
    }

    private OAuthCallbackOutcome doCompleteCallback(OAuthProvider provider, String code, String rawState) {
        Instant now = clock.instant();
        OAuthAuthorizationState state = states.findLockedByStateHash(Sha256.hex(rawState))
                .filter(candidate -> candidate.canBeConsumedAt(now) && candidate.provider() == provider)
                .orElseThrow(() -> {
                    LOG.warn("Falha no login OAuth: state ausente, expirado, já consumido ou de outro provider (provider={})", provider);
                    return new OAuthLoginFailedException();
                });
        state.consume(now);
        UUID linkingUserId = state.linkingUserId();
        OAuthClientKind clientKind = state.client();

        OAuthProviderClient client = clientFor(provider);
        String accessToken;
        OAuthProviderClient.ProviderProfile profile;
        try {
            accessToken = client.exchangeCode(code);
            profile = client.fetchProfile(accessToken);
        } catch (RuntimeException exception) {
            LOG.warn("Falha no login OAuth: erro ao trocar o código ou buscar o perfil no provider (provider={})",
                    provider, exception);
            throw failure(linkingUserId);
        }
        if (profile.email() == null) {
            LOG.warn("Falha no login OAuth: provider não retornou e-mail (provider={})", provider);
            throw failure(linkingUserId);
        }
        EmailAddress email;
        try {
            email = EmailAddress.from(profile.email());
        } catch (IllegalArgumentException exception) {
            LOG.warn("Falha no login OAuth: e-mail retornado pelo provider é inválido (provider={})", provider);
            throw failure(linkingUserId);
        }

        if (linkingUserId != null) {
            linkProviderToExistingAccount(provider, profile.providerUserId(), linkingUserId, email, now);
            return new OAuthCallbackOutcome.Linked(provider);
        }

        UUID userId = resolveUserId(provider, profile.providerUserId(), email, now);
        boolean requiresMfa = totpCredentials.findById(userId).map(TotpCredential::requiresMfaAtLogin).orElse(false);
        if (requiresMfa) {
            // O segundo fator é resolvido pelo cliente, que já sabe falar com /auth/mfa/verify.
            return new OAuthCallbackOutcome.LoggedIn(
                    new AuthenticationService.LoginOutcome(null, mfaLogin.createChallenge(userId)), clientKind);
        }
        if (clientKind == OAuthClientKind.MOBILE) {
            return new OAuthCallbackOutcome.HandedOffToMobile(issueMobileHandoff(userId, now));
        }
        return new OAuthCallbackOutcome.LoggedIn(
                new AuthenticationService.LoginOutcome(sessions.start(userId), null), clientKind);
    }

    private void linkProviderToExistingAccount(
            OAuthProvider provider, String providerUserId, UUID linkingUserId, EmailAddress email, Instant now) {
        Optional<IdentityProviderLink> existingLink = links.findByProviderAndProviderUserId(provider, providerUserId);
        if (existingLink.isPresent()) {
            if (!existingLink.get().userId().equals(linkingUserId)) {
                LOG.warn("Falha ao conectar provider: identidade já vinculada a outra conta (provider={})", provider);
                throw new OAuthLinkFailedException();
            }
            return;
        }
        boolean alreadyHasThisProvider = links.findByUserId(linkingUserId).stream()
                .anyMatch(link -> link.provider() == provider);
        if (alreadyHasThisProvider) {
            LOG.warn("Falha ao conectar provider: a conta já tem esse provider vinculado (provider={})", provider);
            throw new OAuthLinkFailedException();
        }
        links.save(IdentityProviderLink.link(linkingUserId, provider, providerUserId, email.value(), now));
    }

    private RuntimeException failure(UUID linkingUserId) {
        return linkingUserId != null ? new OAuthLinkFailedException() : new OAuthLoginFailedException();
    }

    private UUID resolveUserId(OAuthProvider provider, String providerUserId, EmailAddress email, Instant now) {
        return links.findByProviderAndProviderUserId(provider, providerUserId)
                .map(IdentityProviderLink::userId)
                .orElseGet(() -> {
                    if (users.findByEmailNormalized(email.value()).isPresent()) {
                        LOG.warn("Falha no login OAuth: e-mail já pertence a uma conta sem vínculo com este "
                                + "provider, vínculo automático não é permitido (provider={})", provider);
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
                .orElseThrow(() -> {
                    LOG.warn("Falha no login OAuth: nenhum client configurado para o provider {}", provider);
                    return new OAuthLoginFailedException();
                });
    }

    private String issueRawState() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
