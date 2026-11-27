package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.investments.domain.AnnualRate;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.CdbPosition;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioSummaryAssemblerTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-26");
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

    @Test
    void emptyPortfolioIsCompleteWithZeroTotals() {
        PortfolioSummary summary = PortfolioSummaryAssembler.assemble(List.of(), List.of());

        assertThat(summary.complete()).isTrue();
        assertThat(summary.totalCost()).isEqualTo(Money.zero());
        assertThat(summary.totalCurrent()).isEqualTo(Money.zero());
        assertThat(summary.totalPnl()).isEqualTo(Money.zero());
        assertThat(summary.stocks()).isEmpty();
        assertThat(summary.cdbs()).isEmpty();
    }

    @Test
    void incompleteWhenUnquotedTickerExcludedFromTotals() {
        StockLot petr = lot("PETR4", 100, "38.47", "2026-11-03");
        StockLot vale = lot("VALE3", 20, "100.00", "2026-11-01");
        StockQuote quote = quote("PETR4", "41.20");

        PortfolioSummary summary = PortfolioSummaryAssembler.assemble(
                List.of(new StockLotView(petr, quote), new StockLotView(vale, null)),
                List.of());

        assertThat(summary.complete()).isFalse();
        assertThat(summary.totalCost()).isEqualTo(Money.brl("3847.00"));
        assertThat(summary.totalCurrent()).isEqualTo(Money.brl("4120.00"));
        assertThat(summary.totalPnl()).isEqualTo(Money.brl("273.00"));
        assertThat(summary.stocks()).hasSize(2);
        PortfolioStockPosition unquoted = summary.stocks().stream()
                .filter(s -> s.ticker().equals("VALE3")).findFirst().orElseThrow();
        assertThat(unquoted.currentValue()).isNull();
        assertThat(unquoted.pnl()).isNull();
        assertThat(unquoted.acquisitionCost()).isEqualTo(Money.brl("2000.00"));
    }

    @Test
    void cdbEntersTotalsWithPnl() {
        CdbPositionView cdb = new CdbPositionView(
                cdbPosition(), Money.brl("11250.00"));

        PortfolioSummary summary = PortfolioSummaryAssembler.assemble(List.of(), List.of(cdb));

        assertThat(summary.complete()).isTrue();
        assertThat(summary.cdbs()).hasSize(1);
        assertThat(summary.cdbs().getFirst().principal()).isEqualTo(Money.brl("10000.00"));
        assertThat(summary.cdbs().getFirst().estimatedValue()).isEqualTo(Money.brl("11250.00"));
        assertThat(summary.cdbs().getFirst().pnl()).isEqualTo(Money.brl("1250.00"));
        assertThat(summary.totalCost()).isEqualTo(Money.brl("10000.00"));
        assertThat(summary.totalCurrent()).isEqualTo(Money.brl("11250.00"));
        assertThat(summary.totalPnl()).isEqualTo(Money.brl("1250.00"));
    }

    @Test
    void totalPnlSumsIncludedPositions() {
        StockLot petr = lot("PETR4", 100, "38.47", "2026-11-03");
        StockQuote quote = quote("PETR4", "41.20");
        CdbPositionView cdb = new CdbPositionView(cdbPosition(), Money.brl("11250.00"));

        PortfolioSummary summary = PortfolioSummaryAssembler.assemble(
                List.of(new StockLotView(petr, quote)), List.of(cdb));

        assertThat(summary.complete()).isTrue();
        assertThat(summary.totalCost()).isEqualTo(Money.brl("13847.00"));
        assertThat(summary.totalCurrent()).isEqualTo(Money.brl("15370.00"));
        assertThat(summary.totalPnl()).isEqualTo(Money.brl("1523.00"));
    }

    private static StockLot lot(String ticker, long quantity, String unitPrice, String purchaseDate) {
        return StockLot.register(OWNER, B3Ticker.of(ticker), quantity, Money.brl(unitPrice),
                LocalDate.parse(purchaseDate), TODAY, NOW);
    }

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(ticker, Money.brl(price), Instant.parse("2026-11-26T20:00:00Z"), NOW);
    }

    private static CdbPosition cdbPosition() {
        return CdbPosition.register(OWNER, Money.brl("10000.00"), AnnualRate.of("0.1250"),
                LocalDate.parse("2025-11-26"), LocalDate.parse("2027-11-26"), TODAY, NOW);
    }
}
