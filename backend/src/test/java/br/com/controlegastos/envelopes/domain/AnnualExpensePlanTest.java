package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.shared.money.Money;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AnnualExpensePlanTest {

    @Test
    void dividesTheAnnualAmountAcrossEveryMonthUntilTheNextDueMonth() {
        AnnualExpensePlan plan = AnnualExpensePlan.monthly(Money.brl("1000.01"), MonthDay.of(1, 10));

        var installments = plan.installmentsFrom(LocalDate.of(2026, 9, 1));

        assertThat(installments).extracting(AnnualExpenseInstallment::month)
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11),
                        YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        assertThat(installments).extracting(AnnualExpenseInstallment::amount)
                .containsExactly(Money.brl("200.00"), Money.brl("200.00"), Money.brl("200.00"),
                        Money.brl("200.00"), Money.brl("200.01"));
    }

    @Test
    void startsTheFollowingCycleWhenTheDueDateHasPassed() {
        AnnualExpensePlan plan = AnnualExpensePlan.monthly(Money.brl("1200.00"), MonthDay.of(1, 10));

        var installments = plan.installmentsFrom(LocalDate.of(2026, 1, 11));

        assertThat(installments).hasSize(12);
        assertThat(installments.getFirst().month()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(installments.getLast().month()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(installments).allSatisfy(installment -> assertThat(installment.amount()).isEqualTo(Money.brl("100.00")));
    }

    @Test
    void createsNoMonthlyProvisionForAOneTimeExpense() {
        AnnualExpensePlan plan = AnnualExpensePlan.oneTime(Money.brl("700.00"), MonthDay.of(11, 20));

        assertThat(plan.installmentsFrom(LocalDate.of(2026, 9, 1))).isEmpty();
    }

    @Test
    @DisplayName("a soma das parcelas é exatamente o valor anual, sem perder centavos")
    void somaDasParcelasFecha() {
        AnnualExpensePlan plan = AnnualExpensePlan.monthly(Money.brl("1000.07"), MonthDay.of(6, 30));

        Money total = plan.installmentsFrom(LocalDate.of(2026, 1, 1)).stream()
                .map(AnnualExpenseInstallment::amount).reduce(Money.zero(), Money::add);

        assertThat(total).isEqualTo(Money.brl("1000.07"));
    }

    @Test
    @DisplayName("no dia do vencimento ainda provisiona o mês atual inteiro")
    void diaDoVencimentoProvisionaMesAtual() {
        AnnualExpensePlan plan = AnnualExpensePlan.monthly(Money.brl("300.00"), MonthDay.of(9, 15));

        assertThat(plan.installmentsFrom(LocalDate.of(2026, 9, 15)))
                .containsExactly(new AnnualExpenseInstallment(YearMonth.of(2026, 9), Money.brl("300.00")));
    }

    @Test
    @DisplayName("vencimento em 29/02 cai em 28/02 em ano não bissexto")
    void vinteNoveDeFevereiro() {
        AnnualExpensePlan plan = AnnualExpensePlan.monthly(Money.brl("100.00"), MonthDay.of(2, 29));

        assertThat(plan.nextDueDate(LocalDate.of(2027, 1, 1))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(plan.nextDueDate(LocalDate.of(2028, 1, 1))).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("valor anual zero lança IllegalArgumentException")
    void valorZeroFalha() {
        assertThatThrownBy(() -> AnnualExpensePlan.monthly(Money.zero(), MonthDay.of(1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O valor anual deve ser positivo");
    }

    @Test
    @DisplayName("valor anual negativo lança IllegalArgumentException")
    void valorNegativoFalha() {
        assertThatThrownBy(() -> AnnualExpensePlan.oneTime(Money.brl("-0.01"), MonthDay.of(1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O valor anual deve ser positivo");
    }

    @Test
    @DisplayName("vencimento ausente lança NullPointerException com mensagem")
    void vencimentoAusenteFalha() {
        assertThatThrownBy(() -> AnnualExpensePlan.monthly(Money.brl("1.00"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A data de vencimento é obrigatória");
    }

    @Test
    @DisplayName("data atual ausente lança NullPointerException com mensagem")
    void dataAtualAusenteFalha() {
        AnnualExpensePlan plan = AnnualExpensePlan.monthly(Money.brl("1.00"), MonthDay.of(1, 1));

        assertThatThrownBy(() -> plan.installmentsFrom(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A data atual é obrigatória");
    }
}
