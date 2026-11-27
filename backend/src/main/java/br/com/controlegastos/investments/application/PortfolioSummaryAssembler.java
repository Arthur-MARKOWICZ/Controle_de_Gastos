package br.com.controlegastos.investments.application;

import br.com.controlegastos.shared.money.Money;
import java.util.List;

/** Monta o {@link PortfolioSummary} a partir dos lotes e CDBs já lidos (design D3). */
public final class PortfolioSummaryAssembler {

    private PortfolioSummaryAssembler() {
    }

    public static PortfolioSummary assemble(List<StockLotView> stockViews, List<CdbPositionView> cdbViews) {
        List<PortfolioStockPosition> stocks = StockPositionAggregator.aggregate(stockViews);
        List<PortfolioCdbPosition> cdbs = cdbViews.stream()
                .map(PortfolioSummaryAssembler::cdb)
                .toList();

        Money totalCost = Money.zero();
        Money totalCurrent = Money.zero();
        Money totalPnl = Money.zero();
        boolean complete = true;

        for (PortfolioStockPosition stock : stocks) {
            if (stock.currentValue() == null) {
                complete = false;
                continue;
            }
            totalCost = totalCost.add(stock.acquisitionCost());
            totalCurrent = totalCurrent.add(stock.currentValue());
            totalPnl = totalPnl.add(stock.pnl());
        }
        for (PortfolioCdbPosition cdb : cdbs) {
            totalCost = totalCost.add(cdb.principal());
            totalCurrent = totalCurrent.add(cdb.estimatedValue());
            totalPnl = totalPnl.add(cdb.pnl());
        }

        return new PortfolioSummary(complete, totalCost, totalCurrent, totalPnl, stocks, cdbs);
    }

    private static PortfolioCdbPosition cdb(CdbPositionView view) {
        Money estimated = view.estimatedValue();
        Money principal = view.position().principal();
        return new PortfolioCdbPosition(
                view.position().id(), principal, estimated, estimated.subtract(principal));
    }
}
