package br.com.controlegastos.investments.web;

import br.com.controlegastos.investments.application.PortfolioCdbPosition;
import br.com.controlegastos.investments.application.PortfolioStockLotDetail;
import br.com.controlegastos.investments.application.PortfolioStockPosition;
import br.com.controlegastos.investments.application.PortfolioSummary;
import br.com.controlegastos.investments.application.PortfolioSummaryService;
import br.com.controlegastos.shared.money.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investments/summary")
class PortfolioSummaryController {

    private final PortfolioSummaryService service;

    PortfolioSummaryController(PortfolioSummaryService service) {
        this.service = service;
    }

    @GetMapping
    SummaryResponse summarize() {
        return SummaryResponse.from(service.summarize());
    }

    record SummaryResponse(
            boolean complete,
            MoneyJson.Dto totalCost,
            MoneyJson.Dto totalCurrent,
            MoneyJson.Dto totalPnl,
            List<StockResponse> stocks,
            List<CdbResponse> cdbs) {

        static SummaryResponse from(PortfolioSummary summary) {
            return new SummaryResponse(
                    summary.complete(),
                    MoneyJson.Dto.of(summary.totalCost()),
                    MoneyJson.Dto.of(summary.totalCurrent()),
                    MoneyJson.Dto.of(summary.totalPnl()),
                    summary.stocks().stream().map(StockResponse::from).toList(),
                    summary.cdbs().stream().map(CdbResponse::from).toList());
        }
    }

    record StockResponse(
            String ticker,
            long quantity,
            MoneyJson.Dto averagePrice,
            MoneyJson.Dto acquisitionCost,
            MoneyJson.Dto currentValue,
            MoneyJson.Dto pnl,
            List<LotResponse> lots) {

        static StockResponse from(PortfolioStockPosition position) {
            return new StockResponse(
                    position.ticker(),
                    position.quantity(),
                    MoneyJson.Dto.of(position.averagePrice()),
                    MoneyJson.Dto.of(position.acquisitionCost()),
                    moneyOrNull(position.currentValue()),
                    moneyOrNull(position.pnl()),
                    position.lots().stream().map(LotResponse::from).toList());
        }
    }

    record LotResponse(
            UUID id,
            long quantity,
            MoneyJson.Dto unitPrice,
            MoneyJson.Dto acquisitionCost,
            String purchaseDate,
            MoneyJson.Dto currentValue,
            MoneyJson.Dto pnl) {

        static LotResponse from(PortfolioStockLotDetail lot) {
            return new LotResponse(
                    lot.id(),
                    lot.quantity(),
                    MoneyJson.Dto.of(lot.unitPrice()),
                    MoneyJson.Dto.of(lot.acquisitionCost()),
                    lot.purchaseDate().toString(),
                    moneyOrNull(lot.currentValue()),
                    moneyOrNull(lot.pnl()));
        }
    }

    record CdbResponse(
            UUID id,
            MoneyJson.Dto principal,
            MoneyJson.Dto estimatedValue,
            MoneyJson.Dto pnl) {

        static CdbResponse from(PortfolioCdbPosition position) {
            return new CdbResponse(
                    position.id(),
                    MoneyJson.Dto.of(position.principal()),
                    MoneyJson.Dto.of(position.estimatedValue()),
                    MoneyJson.Dto.of(position.pnl()));
        }
    }

    private static MoneyJson.Dto moneyOrNull(Money money) {
        return money == null ? null : MoneyJson.Dto.of(money);
    }
}
