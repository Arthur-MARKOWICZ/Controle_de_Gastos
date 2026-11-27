package br.com.controlegastos.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.shared.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitSpendingPaceCalculatorTest {

    @Test
    @DisplayName("mês de 31 dias no meio: projeção no limite e allowance 9.55")
    void midOctoberThirtyOneDays() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("310.00"), Money.brl("100.00"),
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 10));

        assertThat(pace.elapsedDays()).isEqualTo(10);
        assertThat(pace.monthLength()).isEqualTo(31);
        assertThat(pace.projectedMonthSpend()).isEqualTo("310.00");
        assertThat(pace.projectedOverrunDay()).isNull();
        assertThat(pace.dailyRemainingAllowance()).isEqualTo("9.55");
    }

    @Test
    @DisplayName("fevereiro com 28 dias")
    void februaryTwentyEightDays() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("280.00"), Money.brl("140.00"),
                YearMonth.of(2026, 2), LocalDate.of(2026, 2, 14));

        assertThat(pace.elapsedDays()).isEqualTo(14);
        assertThat(pace.monthLength()).isEqualTo(28);
        assertThat(pace.projectedMonthSpend()).isEqualTo("280.00");
        assertThat(pace.projectedOverrunDay()).isNull();
    }

    @Test
    @DisplayName("fevereiro bissexto com 29 dias arredonda projeção com HALF_UP")
    void leapFebruaryTwentyNineDays() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("290.00"), Money.brl("145.00"),
                YearMonth.of(2024, 2), LocalDate.of(2024, 2, 15));

        assertThat(pace.monthLength()).isEqualTo(29);
        assertThat(pace.projectedMonthSpend()).isEqualTo("280.33");
    }

    @Test
    @DisplayName("gasto zero: projeção 0.00, sem estouro, allowance = capacity / remainingDays")
    void zeroSpending() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("500.00"), Money.zero(),
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 10));

        assertThat(pace.projectedMonthSpend()).isEqualTo("0.00");
        assertThat(pace.projectedOverrunDay()).isNull();
        assertThat(pace.dailyRemainingAllowance()).isEqualTo("22.73");
    }

    @Test
    @DisplayName("primeiro dia do mês usa elapsedDays = 1")
    void firstCalendarDay() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("310.00"), Money.brl("50.00"),
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 1));

        assertThat(pace.elapsedDays()).isEqualTo(1);
        assertThat(pace.projectedMonthSpend()).isEqualTo("1550.00");
        assertThat(pace.projectedOverrunDay()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    @DisplayName("ritmo projeta estouro no dia 20")
    void projectsOverrunDay() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("400.00"), Money.brl("200.00"),
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 10));

        assertThat(pace.projectedMonthSpend()).isEqualTo("620.00");
        assertThat(pace.projectedOverrunDay()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(pace.dailyRemainingAllowance()).isEqualTo("9.09");
    }

    @Test
    @DisplayName("já acima da capacidade: allowance 0.00 e sem dia de estouro")
    void alreadyOverCapacity() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("100.00"), Money.brl("150.00"),
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 10));

        assertThat(pace.dailyRemainingAllowance()).isEqualTo("0.00");
        assertThat(pace.projectedOverrunDay()).isNull();
    }

    @Test
    @DisplayName("mês passado usa o último dia como referência")
    void pastMonthUsesLastDay() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("310.00"), Money.brl("310.00"),
                YearMonth.of(2026, 9), LocalDate.of(2026, 10, 10));

        assertThat(pace.elapsedDays()).isEqualTo(30);
        assertThat(pace.monthLength()).isEqualTo(30);
        assertThat(pace.projectedMonthSpend()).isEqualTo("310.00");
        assertThat(pace.dailyRemainingAllowance()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("mês futuro usa o primeiro dia como referência")
    void futureMonthUsesFirstDay() {
        var pace = LimitSpendingPaceCalculator.calculate(
                Money.brl("310.00"), Money.zero(),
                YearMonth.of(2026, 12), LocalDate.of(2026, 10, 10));

        assertThat(pace.elapsedDays()).isEqualTo(1);
        assertThat(pace.monthLength()).isEqualTo(31);
        assertThat(pace.projectedMonthSpend()).isEqualTo("0.00");
        assertThat(pace.dailyRemainingAllowance()).isEqualTo("10.00");
    }
}
