package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.domain.UserStatus;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final UserAccountService service = new UserAccountService(users, authentication);

    @Test
    @DisplayName("retorna a conta do usuário autenticado")
    void retornaContaAtual() {
        UserAccount user = UserAccount.register(EmailAddress.from("atual@example.com"), "hash", NOW);
        when(authentication.currentUserId()).thenReturn(user.id());
        when(users.findById(user.id())).thenReturn(Optional.of(user));

        assertThat(service.currentAccount()).isSameAs(user);
    }

    @Test
    @DisplayName("conta removida depois do login lança InvalidCredentialsException")
    void contaRemovidaFalha() {
        UUID ghost = UUID.randomUUID();
        when(authentication.currentUserId()).thenReturn(ghost);
        when(users.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(service::currentAccount).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("sem autenticação no contexto a falha do provedor de identidade propaga")
    void semAutenticacaoPropaga() {
        when(authentication.currentUserId()).thenThrow(new IllegalStateException("sem autenticação"));

        assertThatThrownBy(service::currentAccount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sem autenticação");
    }

    @Test
    @DisplayName("nunca devolve a conta de outro usuário")
    void naoDevolveContaDeOutro() {
        UserAccount other = UserAccount.register(EmailAddress.from("outro@example.com"), "hash", NOW);
        UUID me = UUID.randomUUID();
        when(authentication.currentUserId()).thenReturn(me);
        when(users.findById(other.id())).thenReturn(Optional.of(other));
        when(users.findById(me)).thenReturn(Optional.empty());

        assertThatThrownBy(service::currentAccount).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("conta bloqueada ainda é devolvida com o status para a camada web decidir")
    void contaBloqueadaDevolvidaComStatus() {
        UserAccount user = UserAccount.register(EmailAddress.from("bloq@example.com"), "hash", NOW);
        user.block(NOW);
        when(authentication.currentUserId()).thenReturn(user.id());
        when(users.findById(user.id())).thenReturn(Optional.of(user));

        assertThat(service.currentAccount().status()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("conta encerrada não expõe o e-mail original")
    void contaEncerradaNaoExpoeEmail() {
        UserAccount user = UserAccount.register(EmailAddress.from("encerrada@example.com"), "hash", NOW);
        user.closeAsDeleted(NOW);
        when(authentication.currentUserId()).thenReturn(user.id());
        when(users.findById(user.id())).thenReturn(Optional.of(user));

        assertThat(service.currentAccount().emailNormalized()).doesNotContain("encerrada");
    }

    @Test
    @DisplayName("identificador inválido no token lança IllegalArgumentException")
    void identificadorInvalidoFalha() {
        when(authentication.currentUserId()).thenThrow(new IllegalArgumentException("Invalid UUID string: x"));

        assertThatThrownBy(service::currentAccount).isInstanceOf(IllegalArgumentException.class);
    }
}
