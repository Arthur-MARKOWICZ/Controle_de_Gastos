package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonthlyCommittedAllocationTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void usesBaseAmountForOrdinaryEnvelopes() {
        Envelope limit = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("400.00"), CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(limit, YearMonth.of(2026, 9)))
                .isEqualTo(Money.brl("400.00"));
    }

    @Test
    void includesMonthlyAnnualInstallmentForTheMonth() {
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "IPVA", Money.brl("1000.01"), MonthDay.of(1, 10), AnnualExpenseFundingMode.MONTHLY, CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(annual, YearMonth.of(2026, 9)))
                .isEqualTo(Money.brl("200.00"));
    }

    @Test
    void ignoresOneTimeAnnualAsMonthlyBase() {
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "Seguro", Money.brl("700.00"), MonthDay.of(11, 20), AnnualExpenseFundingMode.ONE_TIME, CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(annual, YearMonth.of(2026, 9)))
                .isEqualTo(Money.zero());
    }

    @Test
    void sumsCommittedAllocationsAcrossEnvelopes() {
        Envelope limit = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("400.00"), CREATED);
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "IPVA", Money.brl("1000.01"), MonthDay.of(1, 10), AnnualExpenseFundingMode.MONTHLY, CREATED);

        assertThat(MonthlyCommittedAllocation.totalFor(List.of(limit, annual), YearMonth.of(2026, 9)))
                .isEqualTo(Money.brl("600.00"));
    }
}
