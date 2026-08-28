package br.com.controlegastos.income.domain;

import br.com.controlegastos.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "income_revision")
public class IncomeRevision {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Convert(converter = br.com.controlegastos.shared.money.MoneyJpaConverter.class)
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money amount;

    @Column(name = "effective_month", nullable = false)
    private LocalDate effectiveMonth;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected IncomeRevision() {
    }

    private IncomeRevision(UUID ownerId, UUID actorUserId, Money amount, YearMonth month, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.actorUserId = actorUserId;
        this.amount = amount;
        this.effectiveMonth = month.atDay(1);
        this.changedAt = changedAt;
    }

    public static IncomeRevision record(UUID ownerId, UUID actorUserId, Money amount,
                                        YearMonth month, Instant changedAt) {
        return new IncomeRevision(ownerId, actorUserId, amount, month, changedAt);
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public Money amount() {
        return amount;
    }

    public YearMonth effectiveMonth() {
        return YearMonth.from(effectiveMonth);
    }

    public Instant changedAt() {
        return changedAt;
    }
}
