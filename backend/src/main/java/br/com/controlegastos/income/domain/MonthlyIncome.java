package br.com.controlegastos.income.domain;

import br.com.controlegastos.income.application.IncomeSnapshot;
import br.com.controlegastos.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "monthly_income")
public class MonthlyIncome {

    @EmbeddedId
    private MonthlyIncomeId id;

    @Convert(converter = br.com.controlegastos.shared.money.MoneyJpaConverter.class)
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MonthlyIncome() {
    }

    private MonthlyIncome(MonthlyIncomeId id, Money amount, Instant updatedAt) {
        this.id = id;
        this.amount = requireNonNegative(amount);
        this.updatedAt = Objects.requireNonNull(updatedAt, "O instante é obrigatório");
    }

    public static MonthlyIncome start(UUID ownerId, YearMonth month, Money amount, Instant now) {
        return new MonthlyIncome(new MonthlyIncomeId(ownerId, month), amount, now);
    }

    public boolean changeTo(Money newAmount, Instant now) {
        Money validAmount = requireNonNegative(newAmount);
        if (amount.equals(validAmount)) {
            return false;
        }
        amount = validAmount;
        updatedAt = Objects.requireNonNull(now, "O instante é obrigatório");
        return true;
    }

    public MonthlyIncomeId id() {
        return id;
    }

    public Money amount() {
        return amount;
    }

    public IncomeSnapshot snapshot() {
        return new IncomeSnapshot(amount, id.effectiveMonth(), updatedAt);
    }

    private static Money requireNonNegative(Money amount) {
        Objects.requireNonNull(amount, "A renda é obrigatória");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("A renda não pode ser negativa");
        }
        return amount;
    }
}
