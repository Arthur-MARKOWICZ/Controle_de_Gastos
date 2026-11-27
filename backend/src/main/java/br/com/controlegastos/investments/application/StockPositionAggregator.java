package br.com.controlegastos.investments.application;

import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrupa lotes por ticker e calcula preço médio (HALF_EVEN) e P&L.
 * O arredondamento fica só neste derivado (design D2).
 */
public final class StockPositionAggregator {

    private StockPositionAggregator() {
    }

    public static List<PortfolioStockPosition> aggregate(List<StockLotView> views) {
        Map<String, List<StockLotView>> byTicker = views.stream()
                .collect(Collectors.groupingBy(
                        view -> view.lot().ticker(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byTicker.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> position(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static PortfolioStockPosition position(String ticker, List<StockLotView> views) {
        long quantity = 0;
        Money acquisitionCost = Money.zero();
        List<PortfolioStockLotDetail> lots = new ArrayList<>(views.size());
        StockQuote quote = views.getFirst().quote();
        for (StockLotView view : views) {
            StockLot lot = view.lot();
            quantity += lot.quantity();
            acquisitionCost = acquisitionCost.add(lot.acquisitionCost());
            Money currentValue = view.currentValue();
            Money pnl = currentValue == null ? null : currentValue.subtract(lot.acquisitionCost());
            lots.add(new PortfolioStockLotDetail(
                    lot.id(), lot.quantity(), lot.unitPrice(), lot.acquisitionCost(),
                    lot.purchaseDate(), currentValue, pnl));
        }
        Money averagePrice = Money.brl(acquisitionCost.amount()
                .divide(BigDecimal.valueOf(quantity), Money.SCALE, RoundingMode.HALF_EVEN));
        Money currentValue = quotedValue(quote, quantity);
        Money pnl = currentValue == null ? null : currentValue.subtract(acquisitionCost);
        return new PortfolioStockPosition(
                ticker, quantity, averagePrice, acquisitionCost, currentValue, pnl, List.copyOf(lots));
    }

    private static Money quotedValue(StockQuote quote, long quantity) {
        if (quote == null) return null;
        try {
            return quote.price().multiply(quantity);
        } catch (IllegalArgumentException beyondMoneyLimit) {
            return null;
        }
    }
}
