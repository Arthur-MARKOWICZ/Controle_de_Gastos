package br.com.controlegastos.ledger.application;

import br.com.controlegastos.envelopes.EnvelopeArchivedEvent;
import br.com.controlegastos.ledger.infrastructure.RecurringEntryRepository;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository;
import java.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Síncrono, na mesma transação do arquivamento (ADR-0031). */
@Component
class RecurringEnvelopeArchiveListener {

    private final RecurringEntryRepository entries;
    private final RecurringOccurrenceRepository occurrences;
    private final Clock clock;

    RecurringEnvelopeArchiveListener(RecurringEntryRepository entries, RecurringOccurrenceRepository occurrences,
                                     Clock clock) {
        this.entries = entries;
        this.occurrences = occurrences;
        this.clock = clock;
    }

    @EventListener
    void on(EnvelopeArchivedEvent event) {
        entries.deactivateByEnvelope(event.envelopeId());
        occurrences.discardPendingByEnvelope(event.envelopeId(), clock.instant());
    }
}
