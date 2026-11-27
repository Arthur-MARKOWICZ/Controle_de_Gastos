package br.com.controlegastos.investments.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.investments.infrastructure.CdbPositionRepository;
import br.com.controlegastos.investments.infrastructure.StockLotRepository;
import br.com.controlegastos.investments.infrastructure.StockQuoteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resumo de leitura da carteira do titular autenticado (design D1). */
@Service
public class PortfolioSummaryService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final StockLotRepository lots;
    private final StockQuoteRepository quotes;
    private final CdbPositionRepository cdbs;
    private final AuthenticationService authentication;
    private final Clock clock;

    public PortfolioSummaryService(StockLotRepository lots, StockQuoteRepository quotes,
                                   CdbPositionRepository cdbs, AuthenticationService authentication,
                                   Clock clock) {
        this.lots = lots;
        this.quotes = quotes;
        this.cdbs = cdbs;
        this.authentication = authentication;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PortfolioSummary summarize() {
        UUID ownerId = authentication.currentUserId();
        List<StockLotView> stockViews = stockViews(ownerId);
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        List<CdbPositionView> cdbViews = cdbs.findListing(ownerId).stream()
                .map(position -> CdbPositionView.of(position, today))
                .toList();
        return PortfolioSummaryAssembler.assemble(stockViews, cdbViews);
    }

    private List<StockLotView> stockViews(UUID ownerId) {
        List<StockLot> listing = lots.findListing(ownerId);
        if (listing.isEmpty()) return List.of();
        Map<String, StockQuote> byTicker = quotes.findAllById(
                        listing.stream().map(StockLot::ticker).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(StockQuote::ticker, Function.identity()));
        return listing.stream()
                .map(lot -> new StockLotView(lot, byTicker.get(lot.ticker())))
                .toList();
    }
}
