package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.shared.money.Money;
import org.junit.jupiter.api.Test;

class EnvelopeBalanceTest {

    @Test
    void carriesUnusedMoneyIntoTheNextMonth() {
        var current = EnvelopeBalance.startWith(Money.brl("100.00"));

        var nextMonth = current.allocate(Money.brl("100.00"));

        assertThat(nextMonth.available()).isEqualTo(Money.brl("200.00"));
        assertThat(nextMonth.isNegative()).isFalse();
    }

    @Test
    void recordsAnExpenseAboveTheAvailableBalanceAndSignalsNegativeBalance() {
        var balance = EnvelopeBalance.startWith(Money.brl("100.00"));

        var afterExpense = balance.spend(Money.brl("125.00"));

        assertThat(afterExpense.available()).isEqualTo(Money.brl("-25.00"));
        assertThat(afterExpense.isNegative()).isTrue();
    }

    @Test
    @DisplayName("gastar exatamente o disponível zera o saldo sem ficar negativo")
    void gastarTudoZera() {
        var balance = EnvelopeBalance.startWith(Money.brl("100.00")).spend(Money.brl("100.00"));

        assertThat(balance.available().toPlainString()).isEqualTo("0.00");
        assertThat(balance.isNegative()).isFalse();
    }

    @Test
    @DisplayName("um centavo além do disponível deixa o saldo negativo")
    void umCentavoAlemNegativa() {
        assertThat(EnvelopeBalance.startWith(Money.brl("0.00")).spend(Money.brl("0.01")).available())
                .isEqualTo(Money.brl("-0.01"));
    }

    @Test
    @DisplayName("aporte negativo lança IllegalArgumentException")
    void aporteNegativoFalha() {
        assertThatThrownBy(() -> EnvelopeBalance.startWith(Money.zero()).allocate(Money.brl("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O aporte não pode ser negativo");
    }

    @Test
    @DisplayName("gasto negativo lança IllegalArgumentException")
    void gastoNegativoFalha() {
        assertThatThrownBy(() -> EnvelopeBalance.startWith(Money.zero()).spend(Money.brl("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O gasto não pode ser negativo");
    }

    @Test
    @DisplayName("gasto ausente lança NullPointerException com mensagem")
    void gastoAusenteFalha() {
        assertThatThrownBy(() -> EnvelopeBalance.startWith(Money.zero()).spend(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O gasto é obrigatório");
    }

    @Test
    @DisplayName("saldo ausente lança NullPointerException com mensagem")
    void saldoAusenteFalha() {
        assertThatThrownBy(() -> EnvelopeBalance.startWith(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O saldo é obrigatório");
    }

    @Test
    @DisplayName("operações não alteram o saldo original")
    void operacoesImutaveis() {
        var original = EnvelopeBalance.startWith(Money.brl("10.00"));
        original.spend(Money.brl("5.00"));

        assertThat(original.available()).isEqualTo(Money.brl("10.00"));
    }
}
