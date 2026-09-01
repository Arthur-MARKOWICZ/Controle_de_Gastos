package br.com.controlegastos.envelopes.domain;

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
}
