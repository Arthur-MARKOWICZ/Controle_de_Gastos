package br.com.controlegastos.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InAppAlertTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private final UUID owner = UUID.randomUUID();
    private final UUID envelope = UUID.randomUUID();

    private InAppAlert alert() {
        return InAppAlert.negativeBalance(owner, envelope, "  Mercado  ", YearMonth.of(2026, 9), NOW);
    }

    @Test
    @DisplayName("alerta de saldo negativo nasce ativo no primeiro dia do mês e com nome aparado")
    void nasceAtivo() {
        InAppAlert alert = alert();

        assertThat(alert.isActive()).isTrue();
        assertThat(alert.type()).isEqualTo(AlertType.NEGATIVE_BALANCE);
        assertThat(alert.referenceMonth()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(alert.envelopeName()).isEqualTo("Mercado");
    }

    @Test
    @DisplayName("dispensar desativa o alerta")
    void dispensarDesativa() {
        InAppAlert alert = alert();

        alert.dismiss(NOW.plusSeconds(1));

        assertThat(alert.isActive()).isFalse();
        assertThat(alert.dismissedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("dispensar de novo não altera a data original")
    void dispensarDeNovoNaoAltera() {
        InAppAlert alert = alert();
        alert.dismiss(NOW.plusSeconds(1));

        alert.dismiss(NOW.plusSeconds(99));

        assertThat(alert.dismissedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("dispensar alerta já resolvido não registra dispensa")
    void dispensarResolvidoNaoRegistra() {
        InAppAlert alert = alert();
        alert.resolve(NOW.plusSeconds(1));

        alert.dismiss(NOW.plusSeconds(2));

        assertThat(alert.dismissedAt()).isNull();
    }

    @Test
    @DisplayName("resolver duas vezes mantém o primeiro instante")
    void resolverDuasVezes() {
        InAppAlert alert = alert();
        alert.resolve(NOW.plusSeconds(1));
        alert.resolve(NOW.plusSeconds(5));

        assertThat(alert.resolvedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("nome em branco lança IllegalArgumentException")
    void nomeEmBrancoFalha() {
        assertThatThrownBy(() -> InAppAlert.negativeBalance(owner, envelope, "   ", YearMonth.of(2026, 9), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O nome da verba deve ter entre 1 e 80 caracteres");
    }

    @Test
    @DisplayName("nome com 81 caracteres lança IllegalArgumentException")
    void nomeLongoFalha() {
        assertThatThrownBy(() -> InAppAlert.negativeBalance(owner, envelope, "x".repeat(81), YearMonth.of(2026, 9), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O nome da verba deve ter entre 1 e 80 caracteres");
    }

    @Test
    @DisplayName("nome ausente lança NullPointerException com mensagem")
    void nomeAusenteFalha() {
        assertThatThrownBy(() -> InAppAlert.negativeBalance(owner, envelope, null, YearMonth.of(2026, 9), NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O nome da verba é obrigatório");
    }

    @Test
    @DisplayName("alerta sem titular lança NullPointerException")
    void semTitularFalha() {
        assertThatThrownBy(() -> InAppAlert.negativeBalance(null, envelope, "Mercado", YearMonth.of(2026, 9), NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
