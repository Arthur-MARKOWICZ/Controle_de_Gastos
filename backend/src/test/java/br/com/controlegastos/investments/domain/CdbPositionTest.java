package br.com.controlegastos.investments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CdbPositionTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.parse("2026-11-26");
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

    @Test
    void estimatedValueAfterFullYearOfAccrual() {
        CdbPosition position = register("10000.00", "0.1250", "2025-11-26", "2027-11-26");

        assertThat(position.estimatedValue(TODAY)).isEqualTo(Money.brl("11250.00"));
        assertThat(position.principal()).isEqualTo(Money.brl("10000.00"));
        assertThat(position.annualRate()).isEqualByComparingTo(new java.math.BigDecimal("0.1250"));
        assertThat(position.startDate()).isEqualTo(LocalDate.parse("2025-11-26"));
        assertThat(position.maturityDate()).isEqualTo(LocalDate.parse("2027-11-26"));
        assertThat(position.ownerId()).isEqualTo(OWNER);
        assertThat(position.id()).isNotNull();
        assertThat(position.createdAt()).isEqualTo(NOW);
    }

    @Test
    void estimateOnStartDateEqualsPrincipal() {
        CdbPosition position = register("10000.00", "0.1250", "2026-11-26", "2027-11-26");

        assertThat(position.estimatedValue(TODAY)).isEqualTo(Money.brl("10000.00"));
    }

    @Test
    void estimateFreezesAtMaturityAndIgnoresLaterToday() {
        CdbPosition position = register("10000.00", "0.1000", "2025-01-15", "2027-01-15");
        Money atMaturity = position.estimatedValue(LocalDate.parse("2027-01-15"));

        assertThat(position.estimatedValue(LocalDate.parse("2028-01-15"))).isEqualTo(atMaturity);
        assertThat(atMaturity).isEqualTo(Money.brl("12100.00"));
    }

    @Test
    void rejectsNonPositivePrincipal() {
        assertThatThrownBy(() -> register("0.00", "0.1250", "2025-11-26", "2027-11-26"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register("-1.00", "0.1250", "2025-11-26", "2027-11-26"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFutureStartDateButAcceptsToday() {
        assertThatThrownBy(() -> register("10000.00", "0.1250", "2026-11-27", "2027-11-26"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(register("10000.00", "0.1250", "2026-11-26", "2027-11-26").startDate()).isEqualTo(TODAY);
    }

    @Test
    void rejectsMaturityOnOrBeforeStart() {
        assertThatThrownBy(() -> register("10000.00", "0.1250", "2025-01-01", "2025-01-01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register("10000.00", "0.1250", "2025-01-01", "2024-12-31"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CdbPosition register(String principal, String rate, String start, String maturity) {
        return CdbPosition.register(OWNER, Money.brl(principal), AnnualRate.of(rate),
                LocalDate.parse(start), LocalDate.parse(maturity), TODAY, NOW);
    }
}
