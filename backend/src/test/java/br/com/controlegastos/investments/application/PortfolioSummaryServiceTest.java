package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.AnnualRate;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.CdbPosition;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.investments.infrastructure.CdbPositionRepository;
import br.com.controlegastos.investments.infrastructure.StockLotRepository;
import br.com.controlegastos.investments.infrastructure.StockQuoteRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioSummaryServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    /** 2026-11-27 02:00 UTC = 2026-11-26 23:00 America/Sao_Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-26");

    @Mock StockLotRepository lots;
    @Mock StockQuoteRepository quotes;
    @Mock CdbPositionRepository cdbs;
    @Mock AuthenticationService authentication;
    @Mock QuoteProvider provider;

    private PortfolioSummaryService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioSummaryService(
                lots, quotes, cdbs, authentication, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void summarizeUsesCurrentTitularAndDoesNotCallQuoteProvider() {
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findListing(OWNER)).thenReturn(List.of());
        when(cdbs.findListing(OWNER)).thenReturn(List.of());

        PortfolioSummary summary = service.summarize();

        assertThat(summary.complete()).isTrue();
        assertThat(summary.stocks()).isEmpty();
        assertThat(summary.cdbs()).isEmpty();
        verify(lots).findListing(OWNER);
        verify(cdbs).findListing(OWNER);
        verifyNoInteractions(provider);
        verifyNoInteractions(quotes);
    }

    @Test
    void summarizeAggregatesLotsQuotesAndCdbsInOrder() {
        StockLot vale = StockLot.register(OWNER, B3Ticker.of("VALE3"), 20, Money.brl("100.00"),
                LocalDate.parse("2026-11-01"), TODAY, NOW);
        StockLot petr100 = StockLot.register(OWNER, B3Ticker.of("PETR4"), 100, Money.brl("38.47"),
                LocalDate.parse("2026-11-03"), TODAY, NOW);
        StockLot petr50 = StockLot.register(OWNER, B3Ticker.of("PETR4"), 50, Money.brl("40.00"),
                LocalDate.parse("2026-11-10"), TODAY, NOW);
        CdbPosition early = CdbPosition.register(OWNER, Money.brl("10000.00"), AnnualRate.of("0.1250"),
                LocalDate.parse("2025-11-26"), LocalDate.parse("2027-11-26"), TODAY, NOW);
        CdbPosition later = CdbPosition.register(OWNER, Money.brl("5000.00"), AnnualRate.of("0.1000"),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"), TODAY, NOW.plusSeconds(1));

        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findListing(OWNER)).thenReturn(List.of(vale, petr100, petr50));
        when(quotes.findAllById(Set.of("VALE3", "PETR4")))
                .thenReturn(List.of(quote("PETR4", "41.20")));
        when(cdbs.findListing(OWNER)).thenReturn(List.of(early, later));

        PortfolioSummary summary = service.summarize();

        assertThat(summary.stocks()).extracting(PortfolioStockPosition::ticker)
                .containsExactly("PETR4", "VALE3");
        assertThat(summary.stocks().getFirst().averagePrice()).isEqualTo(Money.brl("38.98"));
        assertThat(summary.stocks().get(1).currentValue()).isNull();
        assertThat(summary.cdbs()).extracting(PortfolioCdbPosition::id)
                .containsExactly(early.id(), later.id());
        assertThat(summary.cdbs().getFirst().estimatedValue()).isEqualTo(Money.brl("11250.00"));
        assertThat(summary.cdbs().getFirst().pnl()).isEqualTo(Money.brl("1250.00"));
        assertThat(summary.complete()).isFalse();
        verify(quotes, times(1)).findAllById(any());
        verifyNoInteractions(provider);
    }

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(ticker, Money.brl(price), Instant.parse("2026-11-26T20:00:00Z"), NOW);
    }
}
