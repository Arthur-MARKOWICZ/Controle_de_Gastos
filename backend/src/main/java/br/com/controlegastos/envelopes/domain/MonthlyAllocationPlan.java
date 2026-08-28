package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import java.util.List;
import java.util.Objects;

public record MonthlyAllocationPlan(
        Money monthlyIncome,
        List<BaseAllocation> allocations,
        Money unallocated) {

    public MonthlyAllocationPlan {
        Objects.requireNonNull(monthlyIncome, "A renda mensal é obrigatória");
        allocations = List.copyOf(Objects.requireNonNull(allocations, "As verbas são obrigatórias"));
        Objects.requireNonNull(unallocated, "O valor não alocado é obrigatório");

        var expectedUnallocated = calculateUnallocated(monthlyIncome, allocations);
        if (!unallocated.equals(expectedUnallocated)) {
            throw new IllegalArgumentException("O valor não alocado não corresponde à renda e às verbas-base");
        }
    }

    public static MonthlyAllocationPlan create(Money monthlyIncome, List<BaseAllocation> allocations) {
        var safeAllocations = List.copyOf(Objects.requireNonNull(allocations, "As verbas são obrigatórias"));
        var unallocated = calculateUnallocated(monthlyIncome, safeAllocations);

        return new MonthlyAllocationPlan(monthlyIncome, safeAllocations, unallocated);
    }

    private static Money calculateUnallocated(Money monthlyIncome, List<BaseAllocation> allocations) {
        Objects.requireNonNull(monthlyIncome, "A renda mensal é obrigatória");
        if (monthlyIncome.isNegative()) {
            throw new IllegalArgumentException("A renda mensal não pode ser negativa");
        }

        var allocated = allocations.stream()
                .map(BaseAllocation::amount)
                .reduce(Money.zero(), Money::add);

        if (allocated.compareTo(monthlyIncome) > 0) {
            throw new AllocationExceedsIncomeException(allocated.subtract(monthlyIncome));
        }

        return monthlyIncome.subtract(allocated);
    }
}
