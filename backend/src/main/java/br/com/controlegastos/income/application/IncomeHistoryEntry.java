package br.com.controlegastos.income.application;

import br.com.controlegastos.income.domain.IncomeRevision;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record IncomeHistoryEntry(
        UUID id,
        Money amount,
        YearMonth effectiveFrom,
        Instant changedAt,
        UUID changedBy
) {
    static IncomeHistoryEntry from(IncomeRevision revision) {
        return new IncomeHistoryEntry(
                revision.id(), revision.amount(), revision.effectiveMonth(),
                revision.changedAt(), revision.actorUserId());
    }
}
