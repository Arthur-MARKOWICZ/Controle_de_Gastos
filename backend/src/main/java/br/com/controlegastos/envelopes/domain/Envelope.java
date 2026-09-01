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

    @Convert(converter = br.com.controlegastos.shared.money.MoneyJpaConverter.class)
    @Column(name = "target_amount", precision = Money.PRECISION, scale = Money.SCALE)
    private Money targetAmount;

    @Column(name = "target_reached_at")
    private Instant targetReachedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Envelope() {
    }

    private Envelope(UUID id, UUID ownerId, String name, EnvelopePurpose purpose, Money baseAmount, Money targetAmount, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = requireValidName(name);
        applyFinancialConfiguration(purpose, baseAmount, targetAmount);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Envelope create(UUID ownerId, String name, EnvelopePurpose purpose, Money baseAmount, Instant now) {
        return create(ownerId, name, purpose, baseAmount, null, now);
    }

    public static Envelope create(UUID ownerId, String name, EnvelopePurpose purpose, Money baseAmount, Money targetAmount, Instant now) {
        return new Envelope(UUID.randomUUID(), ownerId, name, purpose, baseAmount, targetAmount, now);
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

    public Money targetAmount() {
        return targetAmount;
    }

    public Instant targetReachedAt() {
        return targetReachedAt;
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
        applyFinancialConfiguration(newPurpose, baseAmount, targetAmount);
    }

    public void changeBaseAmount(Money newAmount) {
        applyFinancialConfiguration(purpose, newAmount, targetAmount);
    }

    public void changeTargetAmount(Money newAmount) {
        applyFinancialConfiguration(purpose, baseAmount, newAmount);
    }

    public void changeFinancialConfiguration(EnvelopePurpose newPurpose, Money newBaseAmount, Money newTargetAmount) {
        applyFinancialConfiguration(newPurpose, newBaseAmount, newTargetAmount);
    }

    public boolean isSavingsTarget() {
        return purpose == EnvelopePurpose.SAVINGS_TARGET;
    }

    public boolean recordTargetReached(Instant now) {
        if (!isSavingsTarget() || targetReachedAt != null) return false;
        targetReachedAt = Objects.requireNonNull(now, "O instante é obrigatório");
        return true;
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

    private void applyFinancialConfiguration(EnvelopePurpose newPurpose, Money newBaseAmount, Money newTargetAmount) {
        EnvelopePurpose requiredPurpose = Objects.requireNonNull(newPurpose, "O propósito é obrigatório");
        Money requiredBaseAmount = requireNonNegative(newBaseAmount);
        if (requiredPurpose == EnvelopePurpose.SAVINGS_TARGET) {
            if (!requiredBaseAmount.equals(Money.zero())) {
                throw new IllegalArgumentException("Meta de acumulação não pode ter valor-base mensal");
            }
            this.targetAmount = requirePositiveTarget(newTargetAmount);
        } else {
            if (newTargetAmount != null) {
                throw new IllegalArgumentException("Somente meta de acumulação pode ter valor-alvo");
            }
            this.targetAmount = null;
            this.targetReachedAt = null;
        }
        this.purpose = requiredPurpose;
        this.baseAmount = requiredBaseAmount;
    }

    private static Money requirePositiveTarget(Money amount) {
        if (amount == null) throw new IllegalArgumentException("O valor-alvo é obrigatório");
        if (amount.isNegative() || amount.equals(Money.zero())) {
            throw new IllegalArgumentException("O valor-alvo deve ser positivo");
        }
        return amount;
    }
}
