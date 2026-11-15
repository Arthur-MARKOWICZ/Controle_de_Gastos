package br.com.controlegastos.ledger.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.ledger.domain.RecurringEntry;
import br.com.controlegastos.ledger.domain.RecurringOccurrence;
import br.com.controlegastos.ledger.infrastructure.RecurringEntryRepository;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository.PendingOccurrenceView;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pendências de recorrência: listar, confirmar e descartar (ADR-0031). */
@Service
public class RecurringOccurrenceService {

    private final RecurringOccurrenceRepository occurrences;
    private final RecurringEntryRepository entries;
    private final RecurringEntryService recurringEntries;
    private final LedgerService ledger;
    private final AuthenticationService authentication;
    private final Clock clock;

    public RecurringOccurrenceService(RecurringOccurrenceRepository occurrences, RecurringEntryRepository entries,
                                      RecurringEntryService recurringEntries, LedgerService ledger,
                                      AuthenticationService authentication, Clock clock) {
        this.occurrences = occurrences;
        this.entries = entries;
        this.recurringEntries = recurringEntries;
        this.ledger = ledger;
        this.authentication = authentication;
        this.clock = clock;
    }

    public record Pending(List<PendingOccurrenceView> current, List<PendingOccurrenceView> overdue) {}

    @Transactional(readOnly = true)
    public Pending pending() {
        YearMonth currentMonth = YearMonth.now(clock.withZone(LedgerService.BUSINESS_ZONE));
        List<PendingOccurrenceView> all = occurrences.findPendingForOwner(authentication.currentUserId());
        List<PendingOccurrenceView> overdue = all.stream()
                .filter(o -> YearMonth.from(o.getCompetence()).isBefore(currentMonth)).toList();
        List<PendingOccurrenceView> current = all.stream()
                .filter(o -> !YearMonth.from(o.getCompetence()).isBefore(currentMonth)).toList();
        return new Pending(current, overdue);
    }

    /** Cria o lançamento pelo mesmo caminho do registro manual: saldo, alertas, meta e auditoria. */
    @Transactional
    public RecurringOccurrence confirm(UUID occurrenceId, Money amount, LocalDate occurredAt) {
        RecurringOccurrence occurrence = findOwned(occurrenceId);
        occurrence.requirePending();
        RecurringEntry recurrence = entries.findById(occurrence.recurringEntryId())
                .orElseThrow(() -> new RecurringNotFoundException(occurrenceId));
        LedgerService.Registration registration = ledger.register(occurrence.envelopeId(), recurrence.kind(),
                amount == null ? occurrence.suggestedAmount() : amount,
                occurredAt == null ? occurrence.dueDate() : occurredAt,
                recurrence.description());
        occurrence.confirm(registration.entry().id(), clock.instant());
        return occurrences.saveAndFlush(occurrence);
    }

    @Transactional
    public void discard(UUID occurrenceId) {
        RecurringOccurrence occurrence = findOwned(occurrenceId);
        occurrence.discard(clock.instant());
        occurrences.saveAndFlush(occurrence);
    }

    private RecurringOccurrence findOwned(UUID occurrenceId) {
        RecurringOccurrence occurrence = occurrences.findById(occurrenceId)
                .orElseThrow(() -> new RecurringNotFoundException(occurrenceId));
        recurringEntries.requireOwnedEnvelope(occurrence.envelopeId());
        return occurrence;
    }
}
