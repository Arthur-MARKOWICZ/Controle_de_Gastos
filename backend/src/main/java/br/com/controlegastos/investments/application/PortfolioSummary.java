package br.com.controlegastos.investments.application;

import br.com.controlegastos.shared.money.Money;
import java.util.List;

/** Resumo da carteira do titular: totais, ações agregadas e CDBs. */
public record PortfolioSummary(
        boolean complete,
        Money totalCost,
        Money totalCurrent,
        Money totalPnl,
        List<PortfolioStockPosition> stocks,
        List<PortfolioCdbPosition> cdbs) {
}
