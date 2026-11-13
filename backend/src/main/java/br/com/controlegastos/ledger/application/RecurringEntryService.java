package br.com.controlegastos.ledger.application;

import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.ledger.domain.RecurringEntry;
import br.com.controlegastos.ledger.infrastructure.RecurringEntryRepository;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de recorrências; só o proprietário da verba opera (ADR-0031). */
@Service
public class RecurringEntryService {

    private final EnvelopeService envelopes;
    private final AuthenticationService authentication;
    private final RecurringEntryRepository entries;
    private final RecurringOccurrenceRepository occurrences;
    private final Clock clock;

    public RecurringEntryService(EnvelopeService envelopes, AuthenticationService authentication,
                                 RecurringEntryRepository entries, RecurringOccurrenceRepository occurrences,
                                 Clock clock) {
        this.envelopes = envelopes;
        this.authentication = authentication;
        this.entries = entries;
        this.occurrences = occurrences;
        this.clock = clock;
    }

    public record Command(LedgerKind kind, Money amount, int dayOfMonth, String description) {}

    @Transactional(readOnly = true)
    public List<RecurringEntry> list(UUID envelopeId) {
        requireOwnedEnvelope(envelopeId);
        return entries.findActiveListing(envelopeId);
    }

    @Transactional
    public RecurringEntry create(UUID envelopeId, Command command) {
        requireOwnedEnvelope(envelopeId);
        return entries.save(RecurringEntry.create(envelopeId, command.kind(), command.amount(), command.dayOfMonth(),
                command.description(), today(), clock.instant()));
    }

    @Transactional
    public RecurringEntry update(UUID envelopeId, UUID recurringId, Command command, boolean active) {
        requireOwnedEnvelope(envelopeId);
        RecurringEntry entry = find(envelopeId, recurringId);
        LocalDate today = today();
        entry.update(command.kind(), command.amount(), command.dayOfMonth(), command.description(), today);
        if (active) entry.activate(today); else entry.deactivate();
        return entries.saveAndFlush(entry);
    }

    @Transactional
    public void delete(UUID envelopeId, UUID recurringId) {
        requireOwnedEnvelope(envelopeId);
        RecurringEntry entry = find(envelopeId, recurringId);
        entry.delete(clock.instant());
        entries.saveAndFlush(entry);
        occurrences.discardPendingByRecurrence(entry.id(), clock.instant());
    }

    /** 404 para terceiros e verbas arquivadas; 403 para participantes. */
    Envelope requireOwnedEnvelope(UUID envelopeId) {
        Envelope envelope = envelopes.getVisible(envelopeId);
        if (!envelope.isOwnedBy(authentication.currentUserId())) {
            throw new EnvelopeForbiddenException("Somente o proprietário gerencia recorrências");
        }
        return envelope;
    }

    private RecurringEntry find(UUID envelopeId, UUID recurringId) {
        return entries.findInEnvelope(recurringId, envelopeId).orElseThrow(() -> new RecurringNotFoundException(recurringId));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(LedgerService.BUSINESS_ZONE));
    }
}
