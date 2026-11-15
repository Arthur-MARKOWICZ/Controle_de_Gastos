package br.com.controlegastos.ledger.domain;

import br.com.controlegastos.shared.money.Money;
import br.com.controlegastos.shared.money.MoneyJpaConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/** Pendência de confirmação de uma recorrência numa competência (ADR-0031). */
@Entity
@Table(name = "recurring_occurrence")
public class RecurringOccurrence {

    @Id
    private UUID id;

    @Column(name = "recurring_entry_id", nullable = false)
    private UUID recurringEntryId;

    @Column(name = "envelope_id", nullable = false)
    private UUID envelopeId;

    @Column(nullable = false)
    private LocalDate competence;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Convert(converter = MoneyJpaConverter.class)
    @Column(name = "suggested_amount", nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money suggestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OccurrenceStatus status;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected RecurringOccurrence() {
    }

    public static RecurringOccurrence pending(UUID recurringEntryId, UUID envelopeId, YearMonth competence,
                                              LocalDate dueDate, Money suggestedAmount, Instant now) {
        RecurringOccurrence occurrence = new RecurringOccurrence();
        occurrence.id = UUID.randomUUID();
        occurrence.recurringEntryId = Objects.requireNonNull(recurringEntryId);
        occurrence.envelopeId = Objects.requireNonNull(envelopeId);
        occurrence.competence = competence.atDay(1);
        occurrence.dueDate = Objects.requireNonNull(dueDate);
        occurrence.suggestedAmount = Objects.requireNonNull(suggestedAmount);
        occurrence.status = OccurrenceStatus.PENDING;
        occurrence.createdAt = Objects.requireNonNull(now);
        return occurrence;
    }

    public UUID id() { return id; }
    public UUID recurringEntryId() { return recurringEntryId; }
    public UUID envelopeId() { return envelopeId; }
    public YearMonth competence() { return YearMonth.from(competence); }
    public LocalDate dueDate() { return dueDate; }
    public Money suggestedAmount() { return suggestedAmount; }
    public OccurrenceStatus status() { return status; }
    public UUID ledgerEntryId() { return ledgerEntryId; }
    public Instant resolvedAt() { return resolvedAt; }

    public void requirePending() {
        if (status != OccurrenceStatus.PENDING) throw new OccurrenceNotPendingException(id);
    }

    public void confirm(UUID entryId, Instant now) {
        requirePending();
        status = OccurrenceStatus.CONFIRMED;
        ledgerEntryId = Objects.requireNonNull(entryId);
        resolvedAt = Objects.requireNonNull(now);
    }

    public void discard(Instant now) {
        requirePending();
        status = OccurrenceStatus.DISCARDED;
        resolvedAt = Objects.requireNonNull(now);
    }
}
