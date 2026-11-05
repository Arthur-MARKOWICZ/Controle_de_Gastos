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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry_revision")
public class LedgerEntryRevision {

    @Id
    private UUID id;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LedgerRevisionAction action;

    @Column(name = "previous_envelope_id", nullable = false)
    private UUID previousEnvelopeId;

    @Convert(converter = br.com.controlegastos.shared.money.MoneyJpaConverter.class)
    @Column(name = "previous_amount", nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money previousAmount;

    @Column(name = "previous_description", length = 140)
    private String previousDescription;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected LedgerEntryRevision() {
    }

    private LedgerEntryRevision(UUID ledgerEntryId, UUID ownerId, UUID actorUserId, LedgerRevisionAction action,
                                UUID previousEnvelopeId, Money previousAmount, String previousDescription,
                                Instant changedAt) {
        this.id = UUID.randomUUID();
        this.ledgerEntryId = Objects.requireNonNull(ledgerEntryId);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.actorUserId = Objects.requireNonNull(actorUserId);
        this.action = Objects.requireNonNull(action);
        this.previousEnvelopeId = Objects.requireNonNull(previousEnvelopeId);
        this.previousAmount = Objects.requireNonNull(previousAmount);
        this.previousDescription = previousDescription;
        this.changedAt = Objects.requireNonNull(changedAt);
    }

    public static LedgerEntryRevision captureBefore(LedgerEntry entry, LedgerRevisionAction action,
                                                    UUID actorUserId, Instant changedAt) {
        return new LedgerEntryRevision(
                entry.id(),
                entry.ownerId(),
                actorUserId,
                action,
                entry.envelopeId(),
                entry.amount(),
                entry.description(),
                changedAt);
    }

    public UUID id() {
        return id;
    }

    public UUID ledgerEntryId() {
        return ledgerEntryId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public LedgerRevisionAction action() {
        return action;
    }

    public UUID previousEnvelopeId() {
        return previousEnvelopeId;
    }

    public Money previousAmount() {
        return previousAmount;
    }

    public String previousDescription() {
        return previousDescription;
    }

    public Instant changedAt() {
        return changedAt;
    }
}
