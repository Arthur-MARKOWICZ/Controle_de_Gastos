package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.AuthAttempt;
import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final String EMAIL = "tentativas@example.com";
    private static final String IP = "203.0.113.20";

    private final AuthAttemptRepository repository = mock(AuthAttemptRepository.class);
    private final Map<String, AuthAttempt> stored = new HashMap<>();
    private final AuthAttemptService service =
            new AuthAttemptService(repository, Clock.fixed(NOW, ZoneOffset.UTC), "segredo-hmac-de-teste");

    @BeforeEach
    void setUp() {
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        when(repository.save(any(AuthAttempt.class))).thenAnswer(i -> {
            AuthAttempt attempt = i.getArgument(0);
            stored.put(keyOf(attempt), attempt);
            return attempt;
        });
        doAnswer(i -> stored.remove(i.<String>getArgument(0))).when(repository).deleteById(anyString());
    }

    private static String keyOf(AuthAttempt attempt) throws Exception {
        Field field = AuthAttempt.class.getDeclaredField("key");
        field.setAccessible(true);
        return (String) field.get(attempt);
    }

    private void failLogin(int times, String email, String ip) {
        for (int i = 0; i < times; i++) {
            service.recordLoginFailure(email, ip);
        }
    }

    @Test
    @DisplayName("login segue permitido abaixo do limite de falhas")
    void loginPermitidoAbaixoDoLimite() {
        failLogin(4, EMAIL, IP);

        assertThatCode(() -> service.assertLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cinco falhas bloqueiam o login com TooManyAttemptsException")
    void cincoFalhasBloqueiamLogin() {
        failLogin(5, EMAIL, IP);

        assertThatThrownBy(() -> service.assertLoginAllowed(EMAIL, IP)).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    @DisplayName("o bloqueio vale para o e-mail normalizado, ignorando caixa e espaços")
    void bloqueioUsaEmailNormalizado() {
        failLogin(5, "  TENTATIVAS@example.com ", IP);

        assertThatThrownBy(() -> service.assertLoginAllowed(EMAIL, IP)).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    @DisplayName("bloqueio de um IP não afeta outro IP")
    void bloqueioIsoladoPorIp() {
        failLogin(5, EMAIL, IP);

        assertThatCode(() -> service.assertLoginAllowed(EMAIL, "198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("login bem-sucedido limpa as falhas acumuladas")
    void sucessoLimpaFalhas() {
        failLogin(4, EMAIL, IP);
        service.clearLoginFailures(EMAIL, IP);
        failLogin(4, EMAIL, IP);

        assertThatCode(() -> service.assertLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a chave guardada é um HMAC e não contém o e-mail nem o IP")
    void chaveNaoExpoeDadosPessoais() throws Exception {
        service.recordLoginFailure(EMAIL, IP);

        String key = keyOf(stored.values().iterator().next());
        assertThat(key).matches("[0-9a-f]{64}").doesNotContain("tentativas").doesNotContain("203.0.113");
    }

    @Test
    @DisplayName("cadastro é negado depois de cinco pedidos do mesmo IP")
    void cadastroNegadoAposLimite() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.consumeRegistrationAttempt(IP)).isTrue();
        }

        assertThat(service.consumeRegistrationAttempt(IP)).isFalse();
    }

    @Test
    @DisplayName("recuperação de senha é negada depois de cinco pedidos")
    void recuperacaoNegadaAposLimite() {
        for (int i = 0; i < 5; i++) {
            service.consumePasswordResetAttempt(EMAIL, IP);
        }

        assertThat(service.consumePasswordResetAttempt(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("e-mail malformado não quebra o controle de tentativas")
    void emailMalformadoNaoQuebra() {
        failLogin(5, "nao-e-email", IP);

        assertThatThrownBy(() -> service.assertLoginAllowed("outro-invalido", IP))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    @DisplayName("falhas de MFA, recuperação e callback OAuth bloqueiam cada escopo separadamente")
    void escoposSaoIsolados() {
        for (int i = 0; i < 5; i++) {
            service.recordMfaVerifyFailure(IP);
        }

        assertThatThrownBy(() -> service.assertMfaVerifyAllowed(IP)).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> service.assertMfaRecoveryAllowed(IP)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertOAuthCallbackAllowed(IP)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertMfaSetupAllowed(IP)).doesNotThrowAnyException();
    }
}
