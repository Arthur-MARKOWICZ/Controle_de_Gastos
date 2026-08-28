package br.com.controlegastos.income.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.domain.MonthlyIncome;
import br.com.controlegastos.income.domain.MonthlyIncomeId;
import br.com.controlegastos.income.infrastructure.IncomeRevisionRepository;
import br.com.controlegastos.income.infrastructure.MonthlyIncomeRepository;
import br.com.controlegastos.income.domain.IncomeBelowAllocationsException;
import br.com.controlegastos.income.domain.IncomeNotConfiguredException;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-01T02:30:00Z");

    @Mock
    MonthlyIncomeRepository incomes;

    @Mock
    IncomeRevisionRepository revisions;

    @Mock
    AuthenticationService authentication;

    @BeforeEach
    void authenticatedOwner() {
        when(authentication.currentUserId()).thenReturn(OWNER);
    }

    @Test
    void createsIncomeForTheCurrentMonthInSaoPauloAndAppendsHistory() {
        IncomeService service = service(List.of());
        when(incomes.findById(new MonthlyIncomeId(OWNER, YearMonth.of(2026, 7))))
                .thenReturn(Optional.empty());
        when(incomes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IncomeSnapshot result = service.change(Money.brl("5000.00"));

        assertThat(result.amount()).isEqualTo(Money.brl("5000.00"));
        assertThat(result.effectiveFrom()).isEqualTo(YearMonth.of(2026, 7));
        verify(incomes).save(any(MonthlyIncome.class));
        verify(revisions).save(any());
    }

    @Test
    void sameValueInTheCurrentMonthIsIdempotent() {
        IncomeService service = service(List.of());
        MonthlyIncome existing = MonthlyIncome.start(
                OWNER, YearMonth.of(2026, 7), Money.brl("5000.00"), NOW);
        when(incomes.findById(existing.id())).thenReturn(Optional.of(existing));

        IncomeSnapshot result = service.change(Money.brl("5000.0"));

        assertThat(result.amount()).isEqualTo(Money.brl("5000.00"));
        verify(incomes, never()).save(any());
        verify(revisions, never()).save(any());
    }

    @Test
    void acceptsZeroButDistinguishesItFromMissingIncome() {
        IncomeService service = service(List.of());
        when(incomes.findById(new MonthlyIncomeId(OWNER, YearMonth.of(2026, 7))))
                .thenReturn(Optional.empty());
        when(incomes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.change(Money.zero()).amount()).isEqualTo(Money.zero());

        when(incomes.findEffectiveAtOrBefore(OWNER, YearMonth.of(2026, 6)))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(YearMonth.of(2026, 6)))
                .isInstanceOf(IncomeNotConfiguredException.class);
    }

    @Test
    void rejectsNegativeIncomeAndReductionBelowExistingBaseAllocations() {
        IncomeChangeConstraint allocations = (owner, month) -> Money.brl("3000.00");
        IncomeService service = service(List.of(allocations));

        assertThatThrownBy(() -> service.change(Money.brl("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.change(Money.brl("2500.00")))
                .isInstanceOf(IncomeBelowAllocationsException.class)
                .satisfies(exception -> {
                    IncomeBelowAllocationsException conflict = (IncomeBelowAllocationsException) exception;
                    assertThat(conflict.requiredMinimum()).isEqualTo(Money.brl("3000.00"));
                    assertThat(conflict.shortfall()).isEqualTo(Money.brl("500.00"));
                });

        verify(incomes, never()).save(any());
    }

    @Test
    void readsOnlyTheAuthenticatedOwnersEffectiveIncome() {
        IncomeService service = service(List.of());
        YearMonth requested = YearMonth.of(2026, 10);
        MonthlyIncome effective = MonthlyIncome.start(
                OWNER, YearMonth.of(2026, 9), Money.brl("6200.00"), NOW);
        when(incomes.findEffectiveAtOrBefore(OWNER, requested)).thenReturn(Optional.of(effective));

        IncomeSnapshot result = service.find(requested);

        assertThat(result.effectiveFrom()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(result.amount()).isEqualTo(Money.brl("6200.00"));
        verify(incomes).findEffectiveAtOrBefore(OWNER, requested);
    }

    private IncomeService service(List<IncomeChangeConstraint> constraints) {
        return new IncomeService(
                incomes,
                revisions,
                authentication,
                Clock.fixed(NOW, ZoneOffset.UTC),
                constraints
        );
    }
}
