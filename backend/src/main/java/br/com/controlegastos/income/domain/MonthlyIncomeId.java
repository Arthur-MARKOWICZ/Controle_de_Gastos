package br.com.controlegastos.income.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MonthlyIncomeId implements Serializable {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "effective_month", nullable = false)
    private LocalDate effectiveMonth;

    protected MonthlyIncomeId() {
    }

    public MonthlyIncomeId(UUID ownerId, YearMonth effectiveMonth) {
        this.ownerId = Objects.requireNonNull(ownerId, "O proprietário é obrigatório");
        this.effectiveMonth = Objects.requireNonNull(effectiveMonth, "O mês é obrigatório").atDay(1);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public YearMonth effectiveMonth() {
        return YearMonth.from(effectiveMonth);
    }

    public LocalDate effectiveMonthDate() {
        return effectiveMonth;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof MonthlyIncomeId other
                && Objects.equals(ownerId, other.ownerId)
                && Objects.equals(effectiveMonth, other.effectiveMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, effectiveMonth);
    }
}
