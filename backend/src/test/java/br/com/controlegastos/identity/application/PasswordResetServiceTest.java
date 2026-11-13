package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.PasswordResetToken;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.PasswordResetTokenRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final String EMAIL = "reset@example.com";
    private static final String IP = "203.0.113.7";
    private static final String NEW_PASSWORD = "uma frase nova e longa";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final AuthAttemptService attempts = mock(AuthAttemptService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final PasswordResetMailSender mail = mock(PasswordResetMailSender.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        service = new PasswordResetService(users, tokens, attempts, sessions, mail, encoder,
                Clock.fixed(NOW, ZoneOffset.UTC), "https://app.example.com/");
        when(attempts.consumePasswordResetAttempt(anyString(), anyString())).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("novo-hash");
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private UserAccount activeUser() {
        UserAccount user = UserAccount.register(EmailAddress.from(EMAIL), "hash-antigo", NOW.minusSeconds(3600));
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(user));
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        return user;
    }

    private String tokenFor(UserAccount user, Instant issuedAt) {
        String raw = "token-bruto-de-teste";
        PasswordResetToken token = PasswordResetToken.issue(user.id(), Sha256.hex(raw), issuedAt, Duration.ofMinutes(15));
        when(tokens.findLockedByTokenHash(Sha256.hex(raw))).thenReturn(Optional.of(token));
        return raw;
    }

    @Test
    @DisplayName("pedido válido invalida tokens anteriores, salva só o hash e envia o link após o commit")
    void pedidoValidoEmiteTokenEEnviaLink() {
        UserAccount user = activeUser();

        service.request(EMAIL, IP);
        verify(mail, never()).sendPasswordReset(anyString(), anyString());
        commit();

        verify(tokens).invalidateActiveForUserId(user.id(), NOW);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq(EMAIL), url.capture());
        assertThat(url.getValue()).startsWith("https://app.example.com/redefinir-senha#token=");
        String rawToken = url.getValue().substring(url.getValue().indexOf("token=") + 6);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().tokenHash()).isEqualTo(Sha256.hex(rawToken)).isNotEqualTo(rawToken);
    }

    @Test
    @DisplayName("e-mail sem conta não emite token nem revela a ausência")
    void emailSemContaNaoEmiteToken() {
        when(users.findByEmailNormalized(EMAIL)).thenReturn(Optional.empty());

        service.request(EMAIL, IP);
        commit();

        verify(tokens, never()).save(any());
        verify(mail, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    @DisplayName("conta bloqueada não recebe token de recuperação")
    void contaBloqueadaNaoRecebeToken() {
        activeUser().block(NOW);

        service.request(EMAIL, IP);

        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("limite de tentativas esgotado ignora o pedido em silêncio")
    void limiteEsgotadoIgnoraPedido() {
        activeUser();
        when(attempts.consumePasswordResetAttempt(EMAIL, IP)).thenReturn(false);

        service.request(EMAIL, IP);

        verify(users, never()).findByEmailNormalized(anyString());
        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("e-mail malformado é ignorado sem lançar erro")
    void emailMalformadoEIgnorado() {
        service.request("nao-e-email", IP);

        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("falha do provedor de e-mail não propaga para o chamador")
    void falhaDoEmailNaoPropaga() {
        activeUser();
        doThrow(new IllegalStateException("smtp fora")).when(mail).sendPasswordReset(anyString(), anyString());

        service.request(EMAIL, IP);
        commit();

        verify(mail).sendPasswordReset(eq(EMAIL), anyString());
    }

    @Test
    @DisplayName("URL pública sem https nunca envia o token por link inseguro")
    void urlPublicaSemHttpsNaoEnviaLink() {
        service = new PasswordResetService(users, tokens, attempts, sessions, mail, encoder,
                Clock.fixed(NOW, ZoneOffset.UTC), "http://app.example.com");
        activeUser();

        service.request(EMAIL, IP);
        commit();

        verify(mail, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    @DisplayName("redefinição válida troca a senha, consome o token e revoga as sessões")
    void redefinicaoValidaTrocaSenha() {
        UserAccount user = activeUser();
        String raw = tokenFor(user, NOW.minusSeconds(60));

        service.reset(raw, NEW_PASSWORD);
        commit();

        assertThat(user.passwordCredential().passwordHash()).isEqualTo("novo-hash");
        assertThat(user.emailVerifiedAt()).isEqualTo(NOW);
        verify(sessions).revokeAllForPasswordReset(user.id());
        verify(mail).sendPasswordChanged(EMAIL);
    }

    @Test
    @DisplayName("token desconhecido lança InvalidPasswordResetTokenException")
    void tokenDesconhecidoFalha() {
        when(tokens.findLockedByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("inexistente", NEW_PASSWORD))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    @DisplayName("token expirado lança InvalidPasswordResetTokenException e não troca a senha")
    void tokenExpiradoFalha() {
        UserAccount user = activeUser();
        String raw = tokenFor(user, NOW.minus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> service.reset(raw, NEW_PASSWORD))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThat(user.passwordCredential().passwordHash()).isEqualTo("hash-antigo");
        verify(sessions, never()).revokeAllForPasswordReset(any());
    }

    @Test
    @DisplayName("token já consumido lança InvalidPasswordResetTokenException")
    void tokenReutilizadoFalha() {
        UserAccount user = activeUser();
        String raw = tokenFor(user, NOW.minusSeconds(60));
        service.reset(raw, NEW_PASSWORD);

        assertThatThrownBy(() -> service.reset(raw, "outra frase longa e nova"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    @DisplayName("token de conta bloqueada lança InvalidPasswordResetTokenException")
    void tokenDeContaBloqueadaFalha() {
        UserAccount user = activeUser();
        String raw = tokenFor(user, NOW.minusSeconds(60));
        user.block(NOW);

        assertThatThrownBy(() -> service.reset(raw, NEW_PASSWORD))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    @DisplayName("senha curta lança IllegalArgumentException e mantém o token disponível")
    void senhaCurtaFalha() {
        UserAccount user = activeUser();
        String raw = tokenFor(user, NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.reset(raw, "curta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A senha deve ter entre 12 e 128 caracteres");
        assertThat(tokens.findLockedByTokenHash(Sha256.hex(raw)).orElseThrow().canBeConsumedAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("senha igual ao e-mail lança IllegalArgumentException")
    void senhaIgualAoEmailFalha() {
        UserAccount user = activeUser();
        String raw = tokenFor(user, NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.reset(raw, EMAIL.toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A senha não pode ser igual ao e-mail");
    }
}
