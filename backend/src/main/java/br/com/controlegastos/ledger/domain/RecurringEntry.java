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

/** Regra de lançamento mensal previsível de uma verba (ADR-0031). */
@Entity
@Table(name = "recurring_entry")
public class RecurringEntry {

    @Id
    private UUID id;

    @Column(name = "envelope_id", nullable = false)
    private UUID envelopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LedgerKind kind;

    @Convert(converter = MoneyJpaConverter.class)
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money amount;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(length = 140)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "first_competence", nullable = false)
    private LocalDate firstCompetence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    protected RecurringEntry() {
    }

    public static RecurringEntry create(UUID envelopeId, LedgerKind kind, Money amount, int dayOfMonth,
                                        String description, LocalDate today, Instant now) {
        RecurringEntry entry = new RecurringEntry();
        entry.id = UUID.randomUUID();
        entry.envelopeId = Objects.requireNonNull(envelopeId);
        entry.createdAt = Objects.requireNonNull(now);
        entry.apply(kind, amount, dayOfMonth, description);
        entry.active = true;
        entry.firstCompetence = entry.schedule().firstCompetence(today).atDay(1);
        return entry;
    }

    public UUID id() { return id; }
    public UUID envelopeId() { return envelopeId; }
    public LedgerKind kind() { return kind; }
    public Money amount() { return amount; }
    public int dayOfMonth() { return dayOfMonth; }
    public String description() { return description; }
    public boolean active() { return active; }
    public YearMonth firstCompetence() { return YearMonth.from(firstCompetence); }
    public Instant createdAt() { return createdAt; }
    public Instant deletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }
    public long version() { return version; }

    public RecurrenceSchedule schedule() {
        return new RecurrenceSchedule(dayOfMonth);
    }

    public void update(LedgerKind newKind, Money newAmount, int newDay, String newDescription, LocalDate today) {
        requireNotDeleted();
        apply(newKind, newAmount, newDay, newDescription);
        postponeFirstCompetence(today);
    }

    public void activate(LocalDate today) {
        requireNotDeleted();
        if (active) return;
        active = true;
        postponeFirstCompetence(today);
    }

    public void deactivate() {
        active = false;
    }

    public void delete(Instant now) {
        if (isDeleted()) return;
        active = false;
        deletedAt = Objects.requireNonNull(now);
    }

    private void apply(LedgerKind newKind, Money newAmount, int newDay, String newDescription) {
        new RecurrenceSchedule(newDay);
        kind = Objects.requireNonNull(newKind, "O tipo é obrigatório");
        amount = requirePositive(newAmount);
        dayOfMonth = newDay;
        description = normalizeDescription(newDescription);
    }

    /** Nunca antecipa: evita gerar uma competência cujo dia já passou no momento da mudança. */
    private void postponeFirstCompetence(LocalDate today) {
        YearMonth candidate = schedule().firstCompetence(today);
        if (candidate.isAfter(firstCompetence())) {
            firstCompetence = candidate.atDay(1);
        }
    }

    private void requireNotDeleted() {
        if (isDeleted()) throw new IllegalStateException("Recorrência excluída");
    }

    private static Money requirePositive(Money amount) {
        Objects.requireNonNull(amount, "O valor é obrigatório");
        if (amount.isNegative() || amount.equals(Money.zero())) {
            throw new IllegalArgumentException("O valor deve ser positivo");
        }
        return amount;
    }

    private static String normalizeDescription(String description) {
        if (description == null) return null;
        String trimmed = description.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 140) throw new IllegalArgumentException("A descrição deve ter até 140 caracteres");
        return trimmed;
    }
}
