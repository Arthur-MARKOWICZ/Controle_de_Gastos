package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.IdentityProviderLinkRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginMethodsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void statusReportsPasswordAndLinkedProviders() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccount account = UserAccount.register(EmailAddress.from("pessoa@example.com"), "hash", NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(links.findByUserId(account.id())).thenReturn(List.of(
                IdentityProviderLink.link(account.id(), OAuthProvider.GITHUB, "gh-1", "pessoa@example.com", NOW)));

        LoginMethodsService service = new LoginMethodsService(users, links, mock(PasswordEncoder.class), CLOCK);

        LoginMethodsService.LoginMethods status = service.status(account.id());

        assertThat(status.hasPassword()).isTrue();
        assertThat(status.linkedProviders()).containsExactly(OAuthProvider.GITHUB);
    }

    @Test
    void addsAPasswordToAProviderOnlyAccount() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserAccount account = UserAccount.registerWithProvider(EmailAddress.from("pessoa@example.com"), NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("uma frase bem segura")).thenReturn("$argon2id$hash");

        LoginMethodsService service = new LoginMethodsService(users, links, passwordEncoder, CLOCK);

        service.addPassword(account.id(), "uma frase bem segura");

        assertThat(account.hasPassword()).isTrue();
        assertThat(account.passwordCredential().passwordHash()).isEqualTo("$argon2id$hash");
    }

    @Test
    void rejectsAddingAPasswordWhenOneAlreadyExists() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccount account = UserAccount.register(EmailAddress.from("pessoa@example.com"), "hash", NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));

        LoginMethodsService service = new LoginMethodsService(users, links, mock(PasswordEncoder.class), CLOCK);

        assertThatThrownBy(() -> service.addPassword(account.id(), "uma frase bem segura"))
                .isInstanceOf(PasswordAlreadySetException.class);
    }

    @Test
    void unlinkingTheOnlyLoginMethodIsRejected() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccount account = UserAccount.registerWithProvider(EmailAddress.from("pessoa@example.com"), NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(links.findByUserId(account.id())).thenReturn(List.of(
                IdentityProviderLink.link(account.id(), OAuthProvider.GOOGLE, "g-1", "pessoa@example.com", NOW)));

        LoginMethodsService service = new LoginMethodsService(users, links, mock(PasswordEncoder.class), CLOCK);

        assertThatThrownBy(() -> service.unlink(account.id(), OAuthProvider.GOOGLE))
                .isInstanceOf(LastLoginMethodException.class);
        verify(links, never()).deleteByUserIdAndProvider(any(), any());
    }

    @Test
    void unlinkingIsAllowedWhenAPasswordRemains() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccount account = UserAccount.register(EmailAddress.from("pessoa@example.com"), "hash", NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(links.findByUserId(account.id())).thenReturn(List.of(
                IdentityProviderLink.link(account.id(), OAuthProvider.GOOGLE, "g-1", "pessoa@example.com", NOW)));

        LoginMethodsService service = new LoginMethodsService(users, links, mock(PasswordEncoder.class), CLOCK);
        service.unlink(account.id(), OAuthProvider.GOOGLE);

        verify(links).deleteByUserIdAndProvider(account.id(), OAuthProvider.GOOGLE);
    }

    @Test
    void unlinkingAnotherRemainingProviderIsAllowedEvenWithoutAPassword() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccount account = UserAccount.registerWithProvider(EmailAddress.from("pessoa@example.com"), NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(links.findByUserId(account.id())).thenReturn(List.of(
                IdentityProviderLink.link(account.id(), OAuthProvider.GOOGLE, "g-1", "pessoa@example.com", NOW),
                IdentityProviderLink.link(account.id(), OAuthProvider.GITHUB, "gh-1", "pessoa@example.com", NOW)));

        LoginMethodsService service = new LoginMethodsService(users, links, mock(PasswordEncoder.class), CLOCK);
        service.unlink(account.id(), OAuthProvider.GOOGLE);

        verify(links).deleteByUserIdAndProvider(account.id(), OAuthProvider.GOOGLE);
    }

    @Test
    void unlinkingAProviderThatIsNotLinkedIsANoOp() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
        UserAccount account = UserAccount.register(EmailAddress.from("pessoa@example.com"), "hash", NOW);
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(links.findByUserId(account.id())).thenReturn(List.of());

        LoginMethodsService service = new LoginMethodsService(users, links, mock(PasswordEncoder.class), CLOCK);
        service.unlink(account.id(), OAuthProvider.GOOGLE);

        verify(links, never()).deleteByUserIdAndProvider(any(), any());
    }
}
