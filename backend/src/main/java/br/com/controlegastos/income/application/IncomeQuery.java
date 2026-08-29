package br.com.controlegastos.income.application;

import br.com.controlegastos.income.infrastructure.MonthlyIncomeRepository;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface("query")
public class IncomeQuery {

    private final MonthlyIncomeRepository incomes;

    IncomeQuery(MonthlyIncomeRepository incomes) {
        this.incomes = incomes;
    }

    @Transactional(readOnly = true)
    public Optional<IncomeSnapshot> findEffective(UUID ownerId, YearMonth month) {
        return incomes.findEffectiveAtOrBefore(ownerId, month).map(m -> m.snapshot());
    }
}
