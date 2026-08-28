package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthlyAllocationPlanTest {

    @Test
    void calculatesTheAmountThatRemainsUnallocated() {
        var plan = MonthlyAllocationPlan.create(
                Money.brl("5000.00"),
                List.of(
                        new BaseAllocation("combustivel", Money.brl("400.00")),
                        new BaseAllocation("doacao", Money.brl("100.00")),
                        new BaseAllocation("investimentos", Money.brl("2000.00"))));

        assertThat(plan.unallocated()).isEqualTo(Money.brl("2500.00"));
    }

    @Test
    void rejectsBaseAllocationsWhoseSumExceedsTheMonthlyIncome() {
        assertThatThrownBy(() -> MonthlyAllocationPlan.create(
                        Money.brl("3000.00"),
                        List.of(
                                new BaseAllocation("combustivel", Money.brl("1000.00")),
                                new BaseAllocation("investimentos", Money.brl("2500.00")))))
                .isInstanceOf(AllocationExceedsIncomeException.class)
                .satisfies(exception -> assertThat(((AllocationExceedsIncomeException) exception).excess())
                        .isEqualTo(Money.brl("500.00")));
    }

    @Test
    void rejectsNegativeBaseAllocations() {
        assertThatThrownBy(() -> new BaseAllocation("livros", Money.brl("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativa");
    }

    @Test
    void rejectsAnInconsistentUnallocatedAmountEvenThroughTheCanonicalConstructor() {
        assertThatThrownBy(() -> new MonthlyAllocationPlan(
                        Money.brl("1000.00"),
                        List.of(new BaseAllocation("livros", Money.brl("100.00"))),
                        Money.brl("1000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não alocado");
    }
}
