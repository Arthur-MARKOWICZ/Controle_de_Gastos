package br.com.controlegastos.ledger.domain;

import br.com.controlegastos.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "envelope_id", nullable = false)
    private UUID envelopeId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Convert(converter = br.com.controlegastos.shared.money.MoneyJpaConverter.class)
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LedgerKind kind;

    @Column(name = "occurred_at", nullable = false)
    private LocalDate occurredAt;

    @Column(length = 140)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(UUID id, UUID envelopeId, UUID ownerId, UUID authorId, Money amount, LedgerKind kind, LocalDate occurredAt, String description, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.envelopeId = Objects.requireNonNull(envelopeId);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.authorId = Objects.requireNonNull(authorId);
        this.amount = requirePositive(amount);
        this.kind = Objects.requireNonNull(kind);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.description = normalizeDescription(description);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static LedgerEntry expense(UUID envelopeId, UUID ownerId, UUID authorId, Money amount, LocalDate occurredAt, String description, Instant now) {
        return new LedgerEntry(UUID.randomUUID(), envelopeId, ownerId, authorId, amount, LedgerKind.EXPENSE, occurredAt, description, now);
    }

    public static LedgerEntry contribution(UUID envelopeId, UUID ownerId, UUID authorId, Money amount, LocalDate occurredAt, String description, Instant now) {
        return new LedgerEntry(UUID.randomUUID(), envelopeId, ownerId, authorId, amount, LedgerKind.CONTRIBUTION, occurredAt, description, now);
    }

    public UUID id() { return id; }
    public UUID envelopeId() { return envelopeId; }
    public UUID ownerId() { return ownerId; }
    public UUID authorId() { return authorId; }
    public Money amount() { return amount; }
    public LedgerKind kind() { return kind; }
    public LocalDate occurredAt() { return occurredAt; }
    public String description() { return description; }
    public Instant createdAt() { return createdAt; }

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
