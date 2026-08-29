package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "envelope")
public class Envelope {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnvelopePurpose purpose;

    @Convert(converter = br.com.controlegastos.shared.money.MoneyJpaConverter.class)
    @Column(name = "base_amount", nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money baseAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Envelope() {
    }

    private Envelope(UUID id, UUID ownerId, String name, EnvelopePurpose purpose, Money baseAmount, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = requireValidName(name);
        this.purpose = Objects.requireNonNull(purpose);
        this.baseAmount = requireNonNegative(baseAmount);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Envelope create(UUID ownerId, String name, EnvelopePurpose purpose, Money baseAmount, Instant now) {
        return new Envelope(UUID.randomUUID(), ownerId, name, purpose, baseAmount, now);
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public EnvelopePurpose purpose() {
        return purpose;
    }

    public Money baseAmount() {
        return baseAmount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public long version() {
        return version;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void rename(String newName) {
        this.name = requireValidName(newName);
    }

    public void changePurpose(EnvelopePurpose newPurpose) {
        this.purpose = Objects.requireNonNull(newPurpose, "O propósito é obrigatório");
    }

    public void changeBaseAmount(Money newAmount) {
        this.baseAmount = requireNonNegative(newAmount);
    }

    public void archive(Instant now) {
        if (isArchived()) return;
        this.archivedAt = Objects.requireNonNull(now, "O instante é obrigatório");
    }

    public boolean canBeSeenBy(UUID userId) {
        if (userId == null) return false;
        return ownerId.equals(userId);
        // participant check is done at service layer via repository
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    private static String requireValidName(String name) {
        if (name == null) throw new IllegalArgumentException("O nome da verba é obrigatório");
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("O nome da verba deve ter entre 1 e 80 caracteres");
        }
        return trimmed;
    }

    private static Money requireNonNegative(Money amount) {
        Objects.requireNonNull(amount, "O valor-base é obrigatório");
        if (amount.isNegative()) throw new IllegalArgumentException("A verba-base não pode ser negativa");
        return amount;
    }
}
