package br.com.controlegastos.ledger.application;

import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import br.com.controlegastos.envelopes.application.EnvelopeNotFoundException;
import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.application.IncomeQuery;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService implements LedgerReportingQuery {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EnvelopeService envelopes;
    private final LedgerEntryRepository entries;
    private final AuthenticationService authentication;
    private final Clock clock;
    private final IncomeQuery incomes;

    public LedgerService(EnvelopeService envelopes, LedgerEntryRepository entries,
                         AuthenticationService authentication, Clock clock, IncomeQuery incomes) {
        this.envelopes = envelopes;
        this.entries = entries;
        this.authentication = authentication;
        this.clock = clock;
        this.incomes = incomes;
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
        return availableAt(envelope, month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public Money availableAt(Envelope envelope, LocalDate until) {
        if (envelope == null || until == null) return Money.zero();
        // If envelope created after requested month, no allocation yet
        YearMonth creationMonth = YearMonth.from(envelope.createdAt().atZone(BUSINESS_ZONE));
        YearMonth untilMonth = YearMonth.from(until);
        if (untilMonth.isBefore(creationMonth)) {
            return Money.zero();
        }
        LocalDate allocationUntil = until;
        if (envelope.archivedAt() != null) {
            LocalDate archivedOn = envelope.archivedAt().atZone(BUSINESS_ZONE).toLocalDate();
            if (archivedOn.isBefore(allocationUntil)) allocationUntil = archivedOn;
        }
        YearMonth allocationMonth = YearMonth.from(allocationUntil);
        long monthsCount = java.time.temporal.ChronoUnit.MONTHS.between(creationMonth, allocationMonth) + 1;
        Money baseTotal = Money.zero();
        // Multiply baseAmount * monthsCount
        for (int i = 0; i < monthsCount; i++) {
            baseTotal = baseTotal.add(envelope.baseAmount());
        }
        BigDecimal expensesRaw = entries.sumAmountUpTo(envelope.id(), LedgerKind.EXPENSE, until);
        BigDecimal contributionsRaw = entries.sumAmountUpTo(envelope.id(), LedgerKind.CONTRIBUTION, until);
        Money expenses = expensesRaw == null ? Money.zero() : Money.brl(expensesRaw);
        Money contributions = contributionsRaw == null ? Money.zero() : Money.brl(contributionsRaw);
        return baseTotal.add(contributions).subtract(expenses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportEnvelope> visibleEnvelopes() {
        return envelopes.listVisibleIncludingArchived().stream()
                .map(envelope -> new ReportEnvelope(envelope.id(), envelope.name(), envelope.purpose().name(),
                        envelope.baseAmount(), envelope.createdAt(), envelope.archivedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportEntry> activeEntries(LocalDate from, LocalDate to) {
        UUID userId = authentication.currentUserId();
        return entries.findActiveVisibleEntries(userId, from, to).stream()
                .map(entry -> new ReportEntry(entry.envelopeId(), entry.kind().name(), entry.amount(), entry.occurredAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money availableAt(UUID envelopeId, LocalDate until) {
        Envelope envelope = envelopes.listVisibleIncludingArchived().stream()
                .filter(candidate -> candidate.id().equals(envelopeId))
                .findFirst()
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));
        return availableAt(envelope, until);
    }

    @Transactional(readOnly = true)
    public HistoryPage history(LocalDate from, LocalDate to, boolean includeDeleted, int page, int size) {
        validateHistoryRange(from, to, page, size);
        UUID userId = authentication.currentUserId();
        Page<LedgerEntry> result = entries.findHistory(userId, from, to, includeDeleted, PageRequest.of(page, size));
        Map<UUID, Envelope> envelopesById = envelopes.listVisibleIncludingArchived().stream()
                .collect(java.util.stream.Collectors.toMap(Envelope::id, Function.identity()));
        List<HistoryEntry> items = result.getContent().stream()
                .map(entry -> historyEntry(entry, envelopesById.get(entry.envelopeId()), userId))
                .toList();
        return new HistoryPage(items, result.getNumber(), result.getSize(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public HistorySummary historySummary(LocalDate from, LocalDate to) {
        validateHistoryRange(from, to, 0, 10);
        UUID userId = authentication.currentUserId();
        List<LedgerEntry> expenses = entries.findActiveHistory(userId, from, to);
        Map<UUID, Envelope> envelopesById = envelopes.listVisibleIncludingArchived().stream()
                .collect(java.util.stream.Collectors.toMap(Envelope::id, Function.identity()));

        Money totalExpenses = expenses.stream().map(LedgerEntry::amount).reduce(Money.zero(), Money::add);
        Money income = incomeForIntersectingMonths(userId, from, to);
        Map<YearMonth, Money> monthly = new java.util.TreeMap<>();
        Map<br.com.controlegastos.envelopes.domain.EnvelopePurpose, Money> byPurpose =
                new java.util.EnumMap<>(br.com.controlegastos.envelopes.domain.EnvelopePurpose.class);
        for (LedgerEntry entry : expenses) {
            monthly.merge(YearMonth.from(entry.occurredAt()), entry.amount(), Money::add);
            Envelope envelope = envelopesById.get(entry.envelopeId());
            if (envelope != null) byPurpose.merge(envelope.purpose(), entry.amount(), Money::add);
        }

        Money accumulated = envelopes.listVisibleIncludingArchived().stream()
                .map(envelope -> availableAt(envelope, to))
                .reduce(Money.zero(), Money::add);
        List<MonthlyTotal> monthlyTotals = monthly.entrySet().stream()
                .map(total -> new MonthlyTotal(total.getKey(), total.getValue())).toList();
        List<PurposeTotal> purposeTotals = java.util.Arrays.stream(br.com.controlegastos.envelopes.domain.EnvelopePurpose.values())
                .map(purpose -> new PurposeTotal(purpose, byPurpose.getOrDefault(purpose, Money.zero())))
                .toList();
        return new HistorySummary(income, totalExpenses, income.subtract(totalExpenses), accumulated, monthlyTotals, purposeTotals);
    }

    @Transactional
    public LedgerEntry editExpense(UUID entryId, UUID destinationEnvelopeId, Money amount, String description) {
        LedgerEntry entry = entries.findById(entryId).orElseThrow(() -> new LedgerEntryNotFoundException(entryId));
        UUID userId = authentication.currentUserId();
        if (!entry.ownerId().equals(userId)) throw new EnvelopeForbiddenException("Somente o proprietário pode editar o gasto");
        Envelope destination = envelopes.getVisible(destinationEnvelopeId);
        if (!destination.isOwnedBy(userId)) throw new EnvelopeForbiddenException("O gasto só pode ser movido para uma verba própria");
        entry.edit(destination.id(), destination.ownerId(), amount, description);
        return entries.save(entry);
    }

    @Transactional
    public void deleteExpense(UUID entryId) {
        LedgerEntry entry = entries.findById(entryId).orElseThrow(() -> new LedgerEntryNotFoundException(entryId));
        if (!entry.ownerId().equals(authentication.currentUserId())) {
            throw new EnvelopeForbiddenException("Somente o proprietário pode excluir o gasto");
        }
        entry.delete(clock.instant());
        entries.save(entry);
    }

    private Money incomeForIntersectingMonths(UUID userId, LocalDate from, LocalDate to) {
        Money total = Money.zero();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            total = total.add(incomes.findEffective(userId, month).map(snapshot -> snapshot.amount()).orElse(Money.zero()));
        }
        return total;
    }

    private HistoryEntry historyEntry(LedgerEntry entry, Envelope envelope, UUID userId) {
        if (envelope == null) throw new IllegalStateException("A verba do lançamento não está disponível");
        String role = envelope.ownerId().equals(userId) ? "OWNER" : "PARTICIPANT";
        return new HistoryEntry(entry, envelope.name(), envelope.purpose(), role);
    }

    private void validateHistoryRange(LocalDate from, LocalDate to, int page, int size) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Intervalo de datas inválido");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginação inválida");
    }

    public record HistoryEntry(LedgerEntry entry, String envelopeName,
                               br.com.controlegastos.envelopes.domain.EnvelopePurpose purpose, String role) { }
    public record HistoryPage(List<HistoryEntry> items, int page, int size, boolean hasNext) { }
    public record MonthlyTotal(YearMonth month, Money amount) { }
    public record PurposeTotal(br.com.controlegastos.envelopes.domain.EnvelopePurpose purpose, Money amount) { }
    public record HistorySummary(Money income, Money expenses, Money netBalance, Money accumulatedBalance,
                                 List<MonthlyTotal> monthlyTotals, List<PurposeTotal> purposeTotals) { }
}
