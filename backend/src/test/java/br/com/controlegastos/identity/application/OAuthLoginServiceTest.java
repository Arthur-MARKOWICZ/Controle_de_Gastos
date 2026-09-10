package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuthLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration HANDOFF_LIFETIME = Duration.ofSeconds(60);
    private static final String RAW_STATE = "raw-state";
    private static final String STATE_HASH = Sha256.hex(RAW_STATE);
    private static final String CODE = "auth-code";
    private static final String PROVIDER_USER_ID = "google-123";
    private static final String EMAIL = "pessoa@example.com";

    @Test
    void completingTheCallbackForANewProviderIdentityCreatesAnAccountAndStartsASession() {
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        SessionService.AuthenticatedSession session = new SessionService.AuthenticatedSession("token", 900, "refresh");

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID)).thenReturn(Optional.empty());
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.empty());
        when(sessions.start(any())).thenReturn(session);

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        AuthenticationService.LoginOutcome outcome = loggedIn(
                service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"));

        assertThat(outcome.requiresMfa()).isFalse();
        assertThat(outcome.session()).isEqualTo(session);
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(userCaptor.capture());
        assertThat(userCaptor.getValue().hasPassword()).isFalse();
        assertThat(userCaptor.getValue().emailNormalized()).isEqualTo(EMAIL);
        ArgumentCaptor<IdentityProviderLink> linkCaptor = ArgumentCaptor.forClass(IdentityProviderLink.class);
        verify(links).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().providerUserId()).isEqualTo(PROVIDER_USER_ID);
        verify(attempts).clearOAuthCallbackFailures("127.0.0.1");
    }

    @Test
    void completingTheCallbackForAnAlreadyLinkedIdentityLogsIntoTheSameAccountWithoutCreatingAnything() {
        UUID existingUserId = UUID.randomUUID();
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        SessionService.AuthenticatedSession session = new SessionService.AuthenticatedSession("token", 900, "refresh");

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        IdentityProviderLink existingLink =
                IdentityProviderLink.link(existingUserId, OAuthProvider.GOOGLE, PROVIDER_USER_ID, EMAIL, NOW);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID))
                .thenReturn(Optional.of(existingLink));
        when(totpCredentials.findById(existingUserId))
                .thenReturn(Optional.of(TotpCredential.initiallyDisabled(existingUserId, NOW)));
        when(sessions.start(existingUserId)).thenReturn(session);

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        AuthenticationService.LoginOutcome outcome = loggedIn(
                service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"));

        assertThat(outcome.session()).isEqualTo(session);
        verify(users, never()).save(any());
        verify(links, never()).save(any());
    }

    @Test
    void completingTheCallbackForALinkedIdentityWithMfaEnabledReturnsAChallengeAndNeverStartsASession() {
        UUID existingUserId = UUID.randomUUID();
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        MfaLoginService.ChallengeIssued challenge = new MfaLoginService.ChallengeIssued("challenge-id", 300);

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        IdentityProviderLink existingLink =
                IdentityProviderLink.link(existingUserId, OAuthProvider.GOOGLE, PROVIDER_USER_ID, EMAIL, NOW);
        TotpCredential enabled = TotpCredential.initiallyDisabled(existingUserId, NOW);
        enabled.startEnrollment("cipher".getBytes(), "nonce".getBytes(), 1, NOW, Duration.ofMinutes(10));
        enabled.confirm(NOW);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID))
                .thenReturn(Optional.of(existingLink));
        when(totpCredentials.findById(existingUserId)).thenReturn(Optional.of(enabled));
        when(mfaLogin.createChallenge(existingUserId)).thenReturn(challenge);

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        AuthenticationService.LoginOutcome outcome = loggedIn(
                service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"));

        assertThat(outcome.requiresMfa()).isTrue();
        assertThat(outcome.challenge()).isEqualTo(challenge);
        verify(sessions, never()).start(any());
    }

    @Test
    void rejectsSilentlyLinkingWhenTheEmailAlreadyBelongsToAnUnlinkedAccount() {
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        UserAccount existingAccount = UserAccount.register(EmailAddress.from(EMAIL), "hash", NOW);

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID)).thenReturn(Optional.empty());
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(existingAccount));

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        assertThatThrownBy(() -> service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"))
                .isInstanceOf(OAuthLoginFailedException.class);

        verify(users, never()).save(any());
        verify(links, never()).save(any());
        verify(attempts).recordOAuthCallbackFailure("127.0.0.1");
    }

    @Test
    void rejectsWhenTheProviderDoesNotReturnAnEmail() {
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, null);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID)).thenReturn(Optional.empty());

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        assertThatThrownBy(() -> service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"))
                .isInstanceOf(OAuthLoginFailedException.class);
    }

    @Test
    void rejectsAnExpiredOrAlreadyConsumedState() {
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState alreadyConsumed =
                OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        alreadyConsumed.consume(NOW);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(alreadyConsumed));

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        assertThatThrownBy(() -> service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"))
                .isInstanceOf(OAuthLoginFailedException.class);
    }

    @Test
    void buildAuthorizationUrlPersistsAStateAndReturnsTheProviderUrl() {
        OAuthProviderClient client = mock(OAuthProviderClient.class);
        when(client.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(client.authorizationUrl(anyString())).thenReturn("https://accounts.google.com/authorize?state=x");
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        String url = service.buildAuthorizationUrl(OAuthProvider.GOOGLE, null);

        assertThat(url).isEqualTo("https://accounts.google.com/authorize?state=x");
        verify(states).save(any(OAuthAuthorizationState.class));
    }

    @Test
    void connectingANewProviderToAnAuthenticatedAccountLinksItWithoutStartingASession() {
        UUID currentUserId = UUID.randomUUID();
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState state =
                OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, currentUserId, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID)).thenReturn(Optional.empty());
        when(links.findByUserId(currentUserId)).thenReturn(List.of());

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        OAuthCallbackOutcome result = service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1");

        assertThat(result).isInstanceOf(OAuthCallbackOutcome.Linked.class);
        ArgumentCaptor<IdentityProviderLink> linkCaptor = ArgumentCaptor.forClass(IdentityProviderLink.class);
        verify(links).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().userId()).isEqualTo(currentUserId);
        verify(sessions, never()).start(any());
        verify(users, never()).save(any());
    }

    @Test
    void rejectsConnectingAProviderIdentityAlreadyLinkedToAnotherAccount() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState state =
                OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, currentUserId, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        IdentityProviderLink linkedToSomeoneElse =
                IdentityProviderLink.link(otherUserId, OAuthProvider.GOOGLE, PROVIDER_USER_ID, EMAIL, NOW);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID))
                .thenReturn(Optional.of(linkedToSomeoneElse));

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        assertThatThrownBy(() -> service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"))
                .isInstanceOf(OAuthLinkFailedException.class);

        verify(links, never()).save(any());
    }

    @Test
    void rejectsConnectingAProviderTheAccountAlreadyHasLinked() {
        UUID currentUserId = UUID.randomUUID();
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState state =
                OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, currentUserId, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        IdentityProviderLink alreadyLinkedToAnotherGoogleAccount =
                IdentityProviderLink.link(currentUserId, OAuthProvider.GOOGLE, "google-other-id", EMAIL, NOW);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID)).thenReturn(Optional.empty());
        when(links.findByUserId(currentUserId)).thenReturn(List.of(alreadyLinkedToAnotherGoogleAccount));

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        assertThatThrownBy(() -> service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1"))
                .isInstanceOf(OAuthLinkFailedException.class);

        verify(links, never()).save(any());
    }

    @Test
    void reconnectingTheSameProviderIdentityToTheSameAccountIsIdempotent() {
        UUID currentUserId = UUID.randomUUID();
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState state =
                OAuthAuthorizationState.issue(STATE_HASH, OAuthProvider.GOOGLE, currentUserId, OAuthClientKind.WEB, NOW, STATE_LIFETIME);
        IdentityProviderLink existingLink =
                IdentityProviderLink.link(currentUserId, OAuthProvider.GOOGLE, PROVIDER_USER_ID, EMAIL, NOW);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID))
                .thenReturn(Optional.of(existingLink));

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, mobileHandoffs(), links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK, STATE_LIFETIME,
                HANDOFF_LIFETIME);

        OAuthCallbackOutcome result = service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1");

        assertThat(result).isInstanceOf(OAuthCallbackOutcome.Linked.class);
        verify(links, never()).save(any());
    }

    @Test
    void aMobileCallbackHandsOffACodeInsteadOfStartingTheSessionRightAway() {
        OAuthProviderClient client = fakeGoogleClient(PROVIDER_USER_ID, EMAIL);
        OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
        OAuthMobileHandoffRepository handoffs = mock(OAuthMobileHandoffRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
        MfaLoginService mfaLogin = mock(MfaLoginService.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);

        OAuthAuthorizationState state = OAuthAuthorizationState.issue(
                STATE_HASH, OAuthProvider.GOOGLE, null, OAuthClientKind.MOBILE, NOW, STATE_LIFETIME);
        when(states.findLockedByStateHash(STATE_HASH)).thenReturn(Optional.of(state));
        when(links.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID)).thenReturn(Optional.empty());
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.empty());

        OAuthLoginService service = new OAuthLoginService(
                List.of(client), states, handoffs, links, users, totpCredentials, mfaLogin, sessions, attempts, CLOCK,
                STATE_LIFETIME, HANDOFF_LIFETIME);

        OAuthCallbackOutcome result = service.completeCallback(OAuthProvider.GOOGLE, CODE, RAW_STATE, "127.0.0.1");

        assertThat(result).isInstanceOf(OAuthCallbackOutcome.HandedOffToMobile.class);
        assertThat(((OAuthCallbackOutcome.HandedOffToMobile) result).code()).isNotBlank();
        // A sessão só nasce quando o aplicativo apresenta o código.
        verify(sessions, never()).start(any());
        verify(handoffs).save(any(OAuthMobileHandoff.class));
    }

    @Test
    void redeemingAHandoffCodeStartsTheSessionAndBurnsTheCode() {
        OAuthMobileHandoffRepository handoffs = mock(OAuthMobileHandoffRepository.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        UUID userId = UUID.randomUUID();
        OAuthMobileHandoff handoff = OAuthMobileHandoff.issue(userId, sha256("raw-code"), NOW, HANDOFF_LIFETIME);
        SessionService.AuthenticatedSession session = new SessionService.AuthenticatedSession("token", 900, "refresh");
        when(handoffs.findLockedByCodeHash(sha256("raw-code"))).thenReturn(Optional.of(handoff));
        when(sessions.start(userId)).thenReturn(session);

        OAuthLoginService service = serviceWith(handoffs, sessions, attempts);

        assertThat(service.redeemMobileHandoff("raw-code", "127.0.0.1")).isEqualTo(session);
        assertThat(handoff.canBeConsumedAt(NOW)).isFalse();
    }

    @Test
    void aHandoffCodeCannotBeRedeemedTwice() {
        OAuthMobileHandoffRepository handoffs = mock(OAuthMobileHandoffRepository.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        OAuthMobileHandoff handoff =
                OAuthMobileHandoff.issue(UUID.randomUUID(), sha256("raw-code"), NOW, HANDOFF_LIFETIME);
        handoff.consume(NOW);
        when(handoffs.findLockedByCodeHash(sha256("raw-code"))).thenReturn(Optional.of(handoff));

        OAuthLoginService service = serviceWith(handoffs, sessions, attempts);

        assertThatThrownBy(() -> service.redeemMobileHandoff("raw-code", "127.0.0.1"))
                .isInstanceOf(OAuthLoginFailedException.class);
        verify(sessions, never()).start(any());
        verify(attempts).recordOAuthCallbackFailure("127.0.0.1");
    }

    @Test
    void anUnknownHandoffCodeFailsWithoutRevealingWhy() {
        OAuthMobileHandoffRepository handoffs = mock(OAuthMobileHandoffRepository.class);
        SessionService sessions = mock(SessionService.class);
        AuthAttemptService attempts = mock(AuthAttemptService.class);
        when(handoffs.findLockedByCodeHash(anyString())).thenReturn(Optional.empty());

        OAuthLoginService service = serviceWith(handoffs, sessions, attempts);

        assertThatThrownBy(() -> service.redeemMobileHandoff("desconhecido", "127.0.0.1"))
                .isInstanceOf(OAuthLoginFailedException.class);
        verify(sessions, never()).start(any());
    }

    private OAuthLoginService serviceWith(
            OAuthMobileHandoffRepository handoffs, SessionService sessions, AuthAttemptService attempts) {
        return new OAuthLoginService(
                List.of(), mock(OAuthAuthorizationStateRepository.class), handoffs,
                mock(IdentityProviderLinkRepository.class), mock(UserAccountRepository.class),
                mock(TotpCredentialRepository.class), mock(MfaLoginService.class), sessions, attempts, CLOCK,
                STATE_LIFETIME, HANDOFF_LIFETIME);
    }

    private static String sha256(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private OAuthMobileHandoffRepository mobileHandoffs() {
        return mock(OAuthMobileHandoffRepository.class);
    }

    private AuthenticationService.LoginOutcome loggedIn(OAuthCallbackOutcome outcome) {
        return ((OAuthCallbackOutcome.LoggedIn) outcome).outcome();
    }

    private OAuthProviderClient fakeGoogleClient(String providerUserId, String email) {
        OAuthProviderClient client = mock(OAuthProviderClient.class);
        when(client.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(client.exchangeCode(CODE)).thenReturn("access-token");
        when(client.fetchProfile("access-token"))
                .thenReturn(new OAuthProviderClient.ProviderProfile(providerUserId, email));
        return client;
    }
}
