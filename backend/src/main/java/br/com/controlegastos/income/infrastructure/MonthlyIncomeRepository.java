package br.com.controlegastos.income.infrastructure;

import br.com.controlegastos.income.domain.MonthlyIncome;
import br.com.controlegastos.income.domain.MonthlyIncomeId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyIncomeRepository extends JpaRepository<MonthlyIncome, MonthlyIncomeId> {

    Optional<MonthlyIncome> findFirstByIdOwnerIdAndIdEffectiveMonthLessThanEqualOrderByIdEffectiveMonthDesc(
            UUID ownerId, LocalDate month);

    default Optional<MonthlyIncome> findEffectiveAtOrBefore(UUID ownerId, YearMonth month) {
        return findFirstByIdOwnerIdAndIdEffectiveMonthLessThanEqualOrderByIdEffectiveMonthDesc(
                ownerId, month.atDay(1));
    }
}
