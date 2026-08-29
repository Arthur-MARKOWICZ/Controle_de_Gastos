package br.com.controlegastos.ledger.application;

import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import br.com.controlegastos.envelopes.application.EnvelopeNotFoundException;
import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRepository;
import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EnvelopeService envelopes;
    private final LedgerEntryRepository entries;
    private final AuthenticationService authentication;
    private final Clock clock;

    public LedgerService(EnvelopeService envelopes, LedgerEntryRepository entries,
                         AuthenticationService authentication, Clock clock) {
        this.envelopes = envelopes;
        this.entries = entries;
        this.authentication = authentication;
        this.clock = clock;
    }

    @Transactional
    public LedgerEntry register(UUID envelopeId, LedgerKind kind, Money amount, LocalDate occurredAt, String description) {
        Envelope envelope = envelopes.getVisible(envelopeId);
        UUID userId = authentication.currentUserId();
        // Authorization: EXPENSE allowed for owner or participant, CONTRIBUTION only owner
        if (kind == LedgerKind.CONTRIBUTION) {
            if (!envelope.ownerId().equals(userId)) {
                throw new EnvelopeForbiddenException("Somente o proprietário pode fazer aportes");
            }
        }

        if (amount == null) throw new IllegalArgumentException("O valor é obrigatório");
        if (occurredAt == null) throw new IllegalArgumentException("A data é obrigatória");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (occurredAt.isAfter(today.plusDays(1))) {
            throw new IllegalArgumentException("A data não pode ser no futuro");
        }

        Instant now = clock.instant();
        LedgerEntry entry = kind == LedgerKind.EXPENSE
                ? LedgerEntry.expense(envelopeId, envelope.ownerId(), userId, amount, occurredAt, description, now)
                : LedgerEntry.contribution(envelopeId, envelope.ownerId(), userId, amount, occurredAt, description, now);
        return entries.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> list(UUID envelopeId, LedgerKind kind, YearMonth month, int page, int size) {
        envelopes.getVisible(envelopeId);
        LocalDate monthStart = null;
        LocalDate monthEnd = null;
        if (month != null) {
            monthStart = month.atDay(1);
            monthEnd = month.atEndOfMonth();
        }
        return entries.findFiltered(envelopeId, kind, monthStart, monthEnd, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Money availableFor(Envelope envelope, YearMonth month) {
        if (envelope == null || month == null) return Money.zero();
        // If envelope created after requested month, no allocation yet
        YearMonth creationMonth = YearMonth.from(envelope.createdAt().atZone(BUSINESS_ZONE));
        if (month.isBefore(creationMonth)) {
            return Money.zero();
        }
        long monthsCount = java.time.temporal.ChronoUnit.MONTHS.between(creationMonth, month) + 1;
        Money baseTotal = Money.zero();
        // Multiply baseAmount * monthsCount
        for (int i = 0; i < monthsCount; i++) {
            baseTotal = baseTotal.add(envelope.baseAmount());
        }
        LocalDate until = month.atEndOfMonth();
        BigDecimal expensesRaw = entries.sumAmountUpTo(envelope.id(), LedgerKind.EXPENSE, until);
        BigDecimal contributionsRaw = entries.sumAmountUpTo(envelope.id(), LedgerKind.CONTRIBUTION, until);
        Money expenses = expensesRaw == null ? Money.zero() : Money.brl(expensesRaw);
        Money contributions = contributionsRaw == null ? Money.zero() : Money.brl(contributionsRaw);
        return baseTotal.add(contributions).subtract(expenses);
    }
}
