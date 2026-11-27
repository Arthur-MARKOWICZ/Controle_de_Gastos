package br.com.controlegastos.investments.application;

import br.com.controlegastos.shared.money.Money;
import java.util.List;

/** Posição de um ticker: lotes agregados com preço médio e P&L. */
public record PortfolioStockPosition(
        String ticker,
        long quantity,
        Money averagePrice,
        Money acquisitionCost,
        Money currentValue,
        Money pnl,
        List<PortfolioStockLotDetail> lots) {
}
