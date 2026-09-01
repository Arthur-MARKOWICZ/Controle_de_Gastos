package br.com.controlegastos.ledger.application;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Read-only boundary consumed by the reporting module. */
@NamedInterface
public interface LedgerReportingQuery {

    List<ReportEnvelope> visibleEnvelopes();

    List<ReportEntry> activeEntries(LocalDate from, LocalDate to);

    Money availableAt(UUID envelopeId, LocalDate until);

    record ReportEnvelope(UUID id, String name, String purpose, Money baseAmount,
                          Instant createdAt, Instant archivedAt) {
    }

    record ReportEntry(UUID envelopeId, String kind, Money amount, LocalDate occurredAt) {
    }
}
