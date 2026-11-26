package br.com.controlegastos.investments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockLotTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.parse("2026-11-26");
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

    @Test
    void acquisitionCostIsQuantityTimesUnitPrice() {
        StockLot lot = register(100, "38.47", "2026-11-03");

        assertThat(lot.ticker()).isEqualTo("PETR4");
        assertThat(lot.quantity()).isEqualTo(100);
        assertThat(lot.unitPrice()).isEqualTo(Money.brl("38.47"));
        assertThat(lot.acquisitionCost()).isEqualTo(Money.brl("3847.00"));
        assertThat(lot.purchaseDate()).isEqualTo(LocalDate.parse("2026-11-03"));
        assertThat(lot.ownerId()).isEqualTo(OWNER);
        assertThat(lot.id()).isNotNull();
        assertThat(lot.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> register(0, "10.00", "2026-11-03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register(-5, "10.00", "2026-11-03")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveUnitPrice() {
        assertThatThrownBy(() -> register(1, "0.00", "2026-11-03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register(1, "-1.00", "2026-11-03")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFuturePurchaseDateButAcceptsToday() {
        assertThatThrownBy(() -> register(1, "10.00", "2026-11-27")).isInstanceOf(IllegalArgumentException.class);
        assertThat(register(1, "10.00", "2026-11-26").purchaseDate()).isEqualTo(TODAY);
    }

    @Test
    void rejectsCostBeyondMoneyLimit() {
        assertThatThrownBy(() -> register(Long.MAX_VALUE, "10.00", "2026-11-03"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StockLot register(long quantity, String unitPrice, String purchaseDate) {
        return StockLot.register(OWNER, B3Ticker.of("PETR4"), quantity, Money.brl(unitPrice),
                LocalDate.parse(purchaseDate), TODAY, NOW);
    }
}
