package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Defines one recurring annual expense and its exact monthly provision schedule. */
public record AnnualExpensePlan(Money annualAmount, MonthDay dueDate, AnnualExpenseFundingMode fundingMode) {

    public AnnualExpensePlan {
        Objects.requireNonNull(annualAmount, "O valor anual é obrigatório");
        if (annualAmount.compareTo(Money.zero()) <= 0) {
            throw new IllegalArgumentException("O valor anual deve ser positivo");
        }
        Objects.requireNonNull(dueDate, "A data de vencimento é obrigatória");
        Objects.requireNonNull(fundingMode, "O modo de provisão é obrigatório");
    }

    public static AnnualExpensePlan monthly(Money annualAmount, MonthDay dueDate) {
        return new AnnualExpensePlan(annualAmount, dueDate, AnnualExpenseFundingMode.MONTHLY);
    }

    public static AnnualExpensePlan oneTime(Money annualAmount, MonthDay dueDate) {
        return new AnnualExpensePlan(annualAmount, dueDate, AnnualExpenseFundingMode.ONE_TIME);
    }

    public List<AnnualExpenseInstallment> installmentsFrom(LocalDate today) {
        Objects.requireNonNull(today, "A data atual é obrigatória");
        if (fundingMode == AnnualExpenseFundingMode.ONE_TIME) return List.of();

        LocalDate nextDueDate = nextDueDate(today);
        YearMonth firstMonth = YearMonth.from(today);
        if (YearMonth.from(today).equals(YearMonth.from(dueInYear(today.getYear())))
                && today.isAfter(dueInYear(today.getYear()))) {
            firstMonth = firstMonth.plusMonths(1);
        }
        YearMonth dueMonth = YearMonth.from(nextDueDate);
        int installments = (int) (java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, dueMonth) + 1);
        return divideAcross(firstMonth, installments);
    }

    public LocalDate nextDueDate(LocalDate today) {
        Objects.requireNonNull(today, "A data atual é obrigatória");
        LocalDate thisYear = dueInYear(today.getYear());
        return today.isAfter(thisYear) ? dueInYear(today.getYear() + 1) : thisYear;
    }

    private List<AnnualExpenseInstallment> divideAcross(YearMonth firstMonth, int installments) {
        BigDecimal count = BigDecimal.valueOf(installments);
        Money regularInstallment = Money.brl(annualAmount.amount().divide(count, Money.SCALE, RoundingMode.DOWN));
        Money allocatedRegularly = Money.brl(regularInstallment.amount().multiply(count));
        int extraCents = annualAmount.subtract(allocatedRegularly).amount().movePointRight(Money.SCALE).intValueExact();
        List<AnnualExpenseInstallment> schedule = new ArrayList<>(installments);
        for (int index = 0; index < installments; index++) {
            Money amount = index >= installments - extraCents
                    ? regularInstallment.add(Money.brl("0.01"))
                    : regularInstallment;
            schedule.add(new AnnualExpenseInstallment(firstMonth.plusMonths(index), amount));
        }
        return List.copyOf(schedule);
    }

    private LocalDate dueInYear(int year) {
        // A cobrança de 29/02 vence em 28/02 quando o ano não é bissexto.
        return dueDate.isValidYear(year) ? dueDate.atYear(year) : LocalDate.of(year, 2, 28);
    }
}
