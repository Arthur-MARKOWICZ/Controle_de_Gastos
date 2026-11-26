package br.com.controlegastos.investments.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.investments.infrastructure.StockLotRepository;
import br.com.controlegastos.investments.infrastructure.StockQuoteRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lotes de ativos da B3, sempre do titular autenticado (ADR-0034). */
@Service
public class StockLotService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final StockLotRepository lots;
    private final StockQuoteRepository quotes;
    private final AuthenticationService authentication;
    private final Clock clock;

    public StockLotService(StockLotRepository lots, StockQuoteRepository quotes,
                           AuthenticationService authentication, Clock clock) {
        this.lots = lots;
        this.quotes = quotes;
        this.authentication = authentication;
        this.clock = clock;
    }

    public record Command(B3Ticker ticker, long quantity, Money unitPrice, LocalDate purchaseDate) {
    }

    /** Grava o lote e devolve a cotação já gravada do ticker; nunca consulta o provedor. */
    @Transactional
    public StockLotView register(Command command) {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        StockLot lot = StockLot.register(authentication.currentUserId(), command.ticker(), command.quantity(),
                command.unitPrice(), command.purchaseDate(), today, clock.instant());
        StockLot saved = lots.save(lot);
        return new StockLotView(saved, quotes.findById(saved.ticker()).orElse(null));
    }

    /** Lotes do titular com a última cotação de cada ticker, lidas numa única consulta. */
    @Transactional(readOnly = true)
    public List<StockLotView> list() {
        List<StockLot> listing = lots.findListing(authentication.currentUserId());
        if (listing.isEmpty()) return List.of();
        Map<String, StockQuote> byTicker = quotes.findAllById(
                        listing.stream().map(StockLot::ticker).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(StockQuote::ticker, Function.identity()));
        return listing.stream().map(lot -> new StockLotView(lot, byTicker.get(lot.ticker()))).toList();
    }

    @Transactional
    public void delete(UUID id) {
        StockLot lot = lots.findByIdAndOwnerId(id, authentication.currentUserId())
                .orElseThrow(StockLotNotFoundException::new);
        lots.delete(lot);
    }
}
