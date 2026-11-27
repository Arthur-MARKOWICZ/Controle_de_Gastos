package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockPositionAggregatorTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-26");
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

    @Test
    void aggregatesTwoLotsOfSameTickerWithAveragePriceAndPnl() {
        StockLot lot100 = lot("PETR4", 100, "38.47", "2026-11-03");
        StockLot lot50 = lot("PETR4", 50, "40.00", "2026-11-10");
        StockQuote quote = quote("PETR4", "41.20");

        List<PortfolioStockPosition> positions = StockPositionAggregator.aggregate(List.of(
                new StockLotView(lot100, quote),
                new StockLotView(lot50, quote)));

        assertThat(positions).hasSize(1);
        PortfolioStockPosition petr = positions.getFirst();
        assertThat(petr.ticker()).isEqualTo("PETR4");
        assertThat(petr.quantity()).isEqualTo(150);
        assertThat(petr.averagePrice()).isEqualTo(Money.brl("38.98"));
        assertThat(petr.acquisitionCost()).isEqualTo(Money.brl("5847.00"));
        assertThat(petr.currentValue()).isEqualTo(Money.brl("6180.00"));
        assertThat(petr.pnl()).isEqualTo(Money.brl("333.00"));
        assertThat(petr.lots()).hasSize(2);
        assertThat(petr.lots().get(0).id()).isEqualTo(lot100.id());
        assertThat(petr.lots().get(0).currentValue()).isEqualTo(Money.brl("4120.00"));
        assertThat(petr.lots().get(0).pnl()).isEqualTo(Money.brl("273.00"));
        assertThat(petr.lots().get(1).id()).isEqualTo(lot50.id());
        assertThat(petr.lots().get(1).currentValue()).isEqualTo(Money.brl("2060.00"));
        assertThat(petr.lots().get(1).pnl()).isEqualTo(Money.brl("60.00"));
    }

    @Test
    void averagePriceUsesHalfEvenRounding() {
        // 10.00 / 3 = 3.333… → HALF_EVEN → 3.33
        StockLot a = lot("ABCD3", 1, "3.33", "2026-11-01");
        StockLot b = lot("ABCD3", 1, "3.33", "2026-11-02");
        StockLot c = lot("ABCD3", 1, "3.34", "2026-11-03");

        PortfolioStockPosition position = StockPositionAggregator.aggregate(List.of(
                new StockLotView(a, null),
                new StockLotView(b, null),
                new StockLotView(c, null))).getFirst();

        assertThat(position.acquisitionCost()).isEqualTo(Money.brl("10.00"));
        assertThat(position.quantity()).isEqualTo(3);
        assertThat(position.averagePrice()).isEqualTo(Money.brl("3.33"));
    }

    @Test
    void tickerWithoutQuoteKeepsCostAndNullsCurrentFields() {
        StockLot lot = lot("VALE3", 20, "100.00", "2026-11-01");

        PortfolioStockPosition position = StockPositionAggregator.aggregate(
                List.of(new StockLotView(lot, null))).getFirst();

        assertThat(position.ticker()).isEqualTo("VALE3");
        assertThat(position.quantity()).isEqualTo(20);
        assertThat(position.averagePrice()).isEqualTo(Money.brl("100.00"));
        assertThat(position.acquisitionCost()).isEqualTo(Money.brl("2000.00"));
        assertThat(position.currentValue()).isNull();
        assertThat(position.pnl()).isNull();
        assertThat(position.lots()).hasSize(1);
        assertThat(position.lots().getFirst().currentValue()).isNull();
        assertThat(position.lots().getFirst().pnl()).isNull();
        assertThat(position.lots().getFirst().acquisitionCost()).isEqualTo(Money.brl("2000.00"));
    }

    @Test
    void distinctTickersStaySeparateAndSorted() {
        StockLot vale = lot("VALE3", 1, "60.00", "2026-11-01");
        StockLot petr = lot("PETR4", 1, "38.47", "2026-11-02");

        List<PortfolioStockPosition> positions = StockPositionAggregator.aggregate(List.of(
                new StockLotView(vale, null),
                new StockLotView(petr, null)));

        assertThat(positions).extracting(PortfolioStockPosition::ticker)
                .containsExactly("PETR4", "VALE3");
    }

    private static StockLot lot(String ticker, long quantity, String unitPrice, String purchaseDate) {
        return StockLot.register(OWNER, B3Ticker.of(ticker), quantity, Money.brl(unitPrice),
                LocalDate.parse(purchaseDate), TODAY, NOW);
    }

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(ticker, Money.brl(price), Instant.parse("2026-11-26T20:00:00Z"), NOW);
    }
}
