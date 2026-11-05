package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alocação comprometida do owner em um mês: bases das verbas comuns e parcela
 * mensal de gastos anuais MONTHLY. ONE_TIME não compete como base mensal.
 */
public final class MonthlyCommittedAllocation {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private MonthlyCommittedAllocation() {
    }

    public static Money amountFor(Envelope envelope, YearMonth month) {
        Objects.requireNonNull(envelope, "A verba é obrigatória");
        Objects.requireNonNull(month, "O mês é obrigatório");
        if (envelope.isArchived()) {
            return Money.zero();
        }
        if (envelope.isAnnualExpense()) {
            if (envelope.annualFundingMode() != AnnualExpenseFundingMode.MONTHLY) {
                return Money.zero();
            }
            return installmentFor(envelope, month);
        }
        return envelope.baseAmount();
    }

    public static Money totalFor(List<Envelope> envelopes, YearMonth month) {
        Objects.requireNonNull(envelopes, "As verbas são obrigatórias");
        return envelopes.stream()
                .map(envelope -> amountFor(envelope, month))
                .reduce(Money.zero(), Money::add);
    }

    private static Money installmentFor(Envelope envelope, YearMonth month) {
        return installmentsUntil(envelope, month.atEndOfMonth()).stream()
                .filter(installment -> installment.month().equals(month))
                .map(AnnualExpenseInstallment::amount)
                .reduce(Money.zero(), Money::add);
    }

    private static List<AnnualExpenseInstallment> installmentsUntil(Envelope envelope, LocalDate until) {
        LocalDate cycleStart = envelope.createdAt().atZone(BUSINESS_ZONE).toLocalDate();
        List<AnnualExpenseInstallment> all = new ArrayList<>();
        while (!cycleStart.isAfter(until)) {
            List<AnnualExpenseInstallment> cycle = envelope.annualExpensePlan().installmentsFrom(cycleStart);
            if (cycle.isEmpty()) {
                break;
            }
            all.addAll(cycle.stream()
                    .filter(installment -> !installment.month().atEndOfMonth().isAfter(until))
                    .toList());
            YearMonth lastMonth = cycle.getLast().month();
            cycleStart = lastMonth.plusMonths(1).atDay(1);
        }
        return all;
    }
}
