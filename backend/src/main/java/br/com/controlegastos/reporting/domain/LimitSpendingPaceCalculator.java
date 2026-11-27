package br.com.controlegastos.reporting.domain;

import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** Cálculo puro de projeção de ritmo para verbas LIMIT (métricas derivadas com HALF_UP). */
public final class LimitSpendingPaceCalculator {

    private LimitSpendingPaceCalculator() {
    }

    public static PaceMetrics calculate(Money capacity, Money spent, YearMonth month, LocalDate today) {
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(spent, "spent");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(today, "today");

        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        LocalDate referenceDate = today;
        if (referenceDate.isBefore(first)) {
            referenceDate = first;
        } else if (referenceDate.isAfter(last)) {
            referenceDate = last;
        }

        int monthLength = month.lengthOfMonth();
        int elapsedDays = referenceDate.getDayOfMonth();
        int remainingDays = monthLength - elapsedDays + 1;
        Money remainingCapacity = capacity.subtract(spent);

        String projectedMonthSpend;
        LocalDate projectedOverrunDay = null;
        if (spent.amount().signum() == 0) {
            projectedMonthSpend = scaleTwo(BigDecimal.ZERO);
        } else {
            BigDecimal dailyPace = spent.amount()
                    .divide(BigDecimal.valueOf(elapsedDays), 8, RoundingMode.HALF_UP);
            BigDecimal projected = dailyPace.multiply(BigDecimal.valueOf(monthLength));
            projectedMonthSpend = scaleTwo(projected);

            boolean projectsOverrun = projected.compareTo(capacity.amount()) > 0
                    && remainingCapacity.amount().signum() > 0;
            if (projectsOverrun) {
                BigDecimal daysToExhaust = capacity.amount()
                        .divide(dailyPace, 8, RoundingMode.HALF_UP);
                int overrunDayOfMonth = daysToExhaust.setScale(0, RoundingMode.CEILING).intValueExact();
                if (overrunDayOfMonth < 1) {
                    overrunDayOfMonth = 1;
                } else if (overrunDayOfMonth > monthLength) {
                    overrunDayOfMonth = monthLength;
                }
                projectedOverrunDay = month.atDay(overrunDayOfMonth);
            }
        }

        String dailyRemainingAllowance;
        if (remainingCapacity.amount().signum() <= 0) {
            dailyRemainingAllowance = scaleTwo(BigDecimal.ZERO);
            projectedOverrunDay = null;
        } else {
            dailyRemainingAllowance = scaleTwo(remainingCapacity.amount()
                    .divide(BigDecimal.valueOf(remainingDays), 8, RoundingMode.HALF_UP));
        }

        return new PaceMetrics(
                projectedMonthSpend,
                projectedOverrunDay,
                dailyRemainingAllowance,
                elapsedDays,
                monthLength);
    }

    private static String scaleTwo(BigDecimal value) {
        return value.setScale(Money.SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    public record PaceMetrics(
            String projectedMonthSpend,
            LocalDate projectedOverrunDay,
            String dailyRemainingAllowance,
            int elapsedDays,
            int monthLength) {
    }
}
