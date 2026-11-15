package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.domain.TotpCredential;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountClosureServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final String PASSWORD = "frase segura de teste";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final SessionService sessions = mock(SessionService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TotpCredentialRepository totp = mock(TotpCredentialRepository.class);
    private final RecoveryCodeRepository recoveryCodes = mock(RecoveryCodeRepository.class);
    private final MfaLoginChallengeRepository challenges = mock(MfaLoginChallengeRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final IdentityProviderLinkRepository links = mock(IdentityProviderLinkRepository.class);
    private final OAuthAuthorizationStateRepository states = mock(OAuthAuthorizationStateRepository.class);
    private final OAuthMobileHandoffRepository handoffs = mock(OAuthMobileHandoffRepository.class);
    private final AccountClosureService service = new AccountClosureService(users, sessions, encoder, totp,
            recoveryCodes, challenges, resetTokens, links, states, handoffs, Clock.fixed(NOW, ZoneOffset.UTC));
    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = UserAccount.register(EmailAddress.from("encerrar@example.com"), "hash", NOW.minusSeconds(86400));
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(encoder.matches(PASSWORD, "hash")).thenReturn(true);
    }

    @Test
    @DisplayName("encerrar com a senha correta revoga acessos, apaga vínculos e pseudonimiza a conta")
    void encerrarComSenhaCorreta() {
        TotpCredential credential = TotpCredential.initiallyDisabled(user.id(), NOW);
        when(totp.findById(user.id())).thenReturn(Optional.of(credential));

        service.closeAccount(user.id(), PASSWORD);

        verify(sessions).revokeAllForAccountClosure(user.id());
        verify(resetTokens).invalidateActiveForUserId(user.id(), NOW);
        verify(recoveryCodes).invalidateActiveForUserId(user.id(), NOW);
        verify(totp).delete(credential);
        verify(challenges).deleteByUserId(user.id());
        verify(links).deleteByUserId(user.id());
        verify(states).deleteByLinkingUserId(user.id());
        verify(handoffs).deleteByUserId(user.id());
        assertThat(user.status()).isEqualTo(UserStatus.DELETED);
        assertThat(user.emailNormalized()).doesNotContain("encerrar");
        assertThat(user.hasPassword()).isFalse();
        verify(users).save(user);
    }

    @Test
    @DisplayName("exporta identidade sem hash de senha e com provedores vinculados")
    void exportaIdentidade() {
        when(links.findByUserId(user.id()))
                .thenReturn(List.of(IdentityProviderLink.link(user.id(), OAuthProvider.GITHUB, "gh-1", "encerrar@example.com", NOW)));

        Map<String, Object> exported = service.exportIdentity(user.id());

        assertThat(exported).containsEntry("email", "encerrar@example.com")
                .containsEntry("hasPassword", true)
                .containsEntry("linkedProviders", List.of("GITHUB"))
                .doesNotContainKey("passwordHash");
        assertThat(exported.values()).doesNotContain("hash");
    }

    @Test
    @DisplayName("senha errada lança InvalidCredentialsException e não revoga nada")
    void senhaErradaNaoEncerra() {
        assertThatThrownBy(() -> service.closeAccount(user.id(), "errada"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(sessions, links, handoffs);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("senha nula lança InvalidCredentialsException")
    void senhaNulaFalha() {
        assertThatThrownBy(() -> service.closeAccount(user.id(), null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("conta só com login social não pode ser encerrada por senha")
    void contaSocialNaoEncerraPorSenha() {
        UserAccount social = UserAccount.registerWithProvider(EmailAddress.from("social@example.com"), NOW);
        when(users.findById(social.id())).thenReturn(Optional.of(social));

        assertThatThrownBy(() -> service.closeAccount(social.id(), PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("conta já encerrada lança InvalidCredentialsException ao encerrar de novo")
    void contaJaEncerradaFalha() {
        user.closeAsDeleted(NOW);

        assertThatThrownBy(() -> service.closeAccount(user.id(), PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessions, never()).revokeAllForAccountClosure(any());
    }

    @Test
    @DisplayName("conta bloqueada não exporta identidade")
    void contaBloqueadaNaoExporta() {
        user.block(NOW);

        assertThatThrownBy(() -> service.exportIdentity(user.id()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("conta inexistente lança InvalidCredentialsException")
    void contaInexistenteFalha() {
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportIdentity(ghost)).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.closeAccount(ghost, PASSWORD)).isInstanceOf(InvalidCredentialsException.class);
    }
}
