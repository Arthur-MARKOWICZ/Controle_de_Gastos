package br.com.controlegastos.envelopes.domain;

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
}
