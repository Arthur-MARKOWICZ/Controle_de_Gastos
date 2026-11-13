package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    private PasswordResetToken issued() {
        return PasswordResetToken.issue(UUID.randomUUID(), "a".repeat(64), NOW, LIFETIME);
    }

    @Test
    @DisplayName("token recém-emitido pode ser consumido uma vez dentro do prazo")
    void tokenRecemEmitidoPodeSerConsumido() {
        PasswordResetToken token = issued();

        assertThat(token.canBeConsumedAt(NOW.plusSeconds(60))).isTrue();
        token.consume(NOW.plusSeconds(60));

        assertThat(token.canBeConsumedAt(NOW.plusSeconds(61))).isFalse();
    }

    @Test
    @DisplayName("token não pode ser consumido no instante exato da expiração")
    void tokenNaoPodeSerConsumidoNaExpiracao() {
        assertThat(issued().canBeConsumedAt(NOW.plus(LIFETIME))).isFalse();
        assertThat(issued().canBeConsumedAt(NOW.plus(LIFETIME).minusMillis(1))).isTrue();
    }

    @Test
    @DisplayName("reutilizar token consumido lança IllegalStateException")
    void reutilizarTokenConsumidoFalha() {
        PasswordResetToken token = issued();
        token.consume(NOW);

        assertThatThrownBy(() -> token.consume(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token de recuperação indisponível");
    }

    @Test
    @DisplayName("consumir token expirado lança IllegalStateException")
    void consumirTokenExpiradoFalha() {
        PasswordResetToken token = issued();

        assertThatThrownBy(() -> token.consume(NOW.plus(LIFETIME).plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token de recuperação indisponível");
    }

    @Test
    @DisplayName("emitir sem usuário lança NullPointerException")
    void emitirSemUsuarioFalha() {
        assertThatThrownBy(() -> PasswordResetToken.issue(null, "hash", NOW, LIFETIME))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("emitir sem hash lança NullPointerException")
    void emitirSemHashFalha() {
        assertThatThrownBy(() -> PasswordResetToken.issue(UUID.randomUUID(), null, NOW, LIFETIME))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("emitir sem prazo de validade lança NullPointerException")
    void emitirSemPrazoFalha() {
        assertThatThrownBy(() -> PasswordResetToken.issue(UUID.randomUUID(), "hash", NOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("guarda o usuário e o hash informados, nunca o token bruto")
    void guardaUsuarioEHash() {
        UUID userId = UUID.randomUUID();
        PasswordResetToken token = PasswordResetToken.issue(userId, "b".repeat(64), NOW, LIFETIME);

        assertThat(token.userId()).isEqualTo(userId);
        assertThat(token.tokenHash()).isEqualTo("b".repeat(64));
    }
}
