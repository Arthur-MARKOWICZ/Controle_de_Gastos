package br.com.controlegastos.investments.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.infrastructure.StockLotRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lotes de ativos da B3, sempre do titular autenticado (ADR-0034). */
@Service
public class StockLotService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final StockLotRepository lots;
    private final AuthenticationService authentication;
    private final Clock clock;

    public StockLotService(StockLotRepository lots, AuthenticationService authentication, Clock clock) {
        this.lots = lots;
        this.authentication = authentication;
        this.clock = clock;
    }

    public record Command(B3Ticker ticker, long quantity, Money unitPrice, LocalDate purchaseDate) {
    }

    @Transactional
    public StockLot register(Command command) {
        LocalDate today = LocalDate.now(clock.withZone(SAO_PAULO));
        StockLot lot = StockLot.register(authentication.currentUserId(), command.ticker(), command.quantity(),
                command.unitPrice(), command.purchaseDate(), today, clock.instant());
        return lots.save(lot);
    }

    @Transactional(readOnly = true)
    public List<StockLot> list() {
        return lots.findListing(authentication.currentUserId());
    }

    @Transactional
    public void delete(UUID id) {
        StockLot lot = lots.findByIdAndOwnerId(id, authentication.currentUserId())
                .orElseThrow(StockLotNotFoundException::new);
        lots.delete(lot);
    }
}
