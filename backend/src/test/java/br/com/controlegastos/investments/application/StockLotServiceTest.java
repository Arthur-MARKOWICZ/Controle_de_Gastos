package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.investments.infrastructure.StockLotRepository;
import br.com.controlegastos.investments.infrastructure.StockQuoteRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockLotServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    /** 2026-11-27 em UTC, mas ainda 2026-11-26 (23h) em America/Sao_Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");

    @Mock StockLotRepository lots;
    @Mock AuthenticationService authentication;
    @Mock StockQuoteRepository quotes;
    @Mock QuoteProvider provider;

    private StockLotService service;

    @BeforeEach
    void setUp() {
        service = new StockLotService(lots, quotes, authentication, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersLotForCurrentTitularUsingSaoPauloDate() {
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.save(any(StockLot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockLotView view = service.register(command("2026-11-26"));

        assertThat(view.lot().ownerId()).isEqualTo(OWNER);
        assertThat(view.lot().createdAt()).isEqualTo(NOW);
        assertThat(view.quote()).isNull();
        assertThat(view.currentValue()).isNull();
        verify(lots).save(view.lot());
        verifyNoInteractions(provider);
    }

    @Test
    void registerReturnsStoredQuoteWithoutCallingProvider() {
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.save(any(StockLot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotes.findById("PETR4")).thenReturn(Optional.of(quote("PETR4", "41.20")));

        StockLotView view = service.register(command("2026-11-26"));

        assertThat(view.quote().price()).isEqualTo(Money.brl("41.20"));
        assertThat(view.currentValue()).isEqualTo(Money.brl("4120.00"));
        verifyNoInteractions(provider);
    }

    @Test
    void rejectsPurchaseAfterTodayInSaoPaulo() {
        when(authentication.currentUserId()).thenReturn(OWNER);

        assertThatThrownBy(() -> service.register(command("2026-11-27"))).isInstanceOf(IllegalArgumentException.class);
        verify(lots, never()).save(any());
    }

    @Test
    void listsOnlyCurrentTitularLots() {
        StockLot lot = lot();
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findListing(OWNER)).thenReturn(List.of(lot));

        assertThat(service.list()).extracting(StockLotView::lot).containsExactly(lot);
    }

    @Test
    void listAttachesQuotesWithOneLookup() {
        StockLot petr = lot("PETR4", 100);
        StockLot petr2 = lot("PETR4", 3);
        StockLot abcd = lot("ABCD3", 5);
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findListing(OWNER)).thenReturn(List.of(petr, petr2, abcd));
        when(quotes.findAllById(Set.of("PETR4", "ABCD3"))).thenReturn(List.of(quote("PETR4", "41.20")));

        List<StockLotView> views = service.list();

        assertThat(views.get(0).currentValue()).isEqualTo(Money.brl("4120.00"));
        assertThat(views.get(1).currentValue()).isEqualTo(Money.brl("123.60"));
        assertThat(views.get(2).quote()).isNull();
        assertThat(views.get(2).currentValue()).isNull();
        assertThat(views.get(2).lot().acquisitionCost()).isEqualTo(Money.brl("50.00"));
        verify(quotes, times(1)).findAllById(any());
        verifyNoInteractions(provider);
    }

    @Test
    void currentValueBeyondMoneyLimitIsNull() {
        StockLot huge = StockLot.register(OWNER, B3Ticker.of("PETR4"), 1_000_000_000_000_000L, Money.brl("0.01"),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-26"), NOW);
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findListing(OWNER)).thenReturn(List.of(huge));
        when(quotes.findAllById(Set.of("PETR4"))).thenReturn(List.of(quote("PETR4", "100000.00")));

        StockLotView view = service.list().getFirst();

        assertThat(view.quote()).isNotNull();
        assertThat(view.currentValue()).isNull();
        assertThat(view.lot().acquisitionCost()).isEqualTo(Money.brl("10000000000000.00"));
    }

    @Test
    void emptyListingSkipsQuoteLookup() {
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findListing(OWNER)).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
        verifyNoInteractions(quotes);
    }

    @Test
    void deletesOwnLot() {
        StockLot lot = lot();
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findByIdAndOwnerId(lot.id(), OWNER)).thenReturn(Optional.of(lot));

        service.delete(lot.id());

        verify(lots).delete(lot);
    }

    @Test
    void deletingUnknownOrForeignLotIsNotFound() {
        UUID id = UUID.randomUUID();
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.findByIdAndOwnerId(id, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(StockLotNotFoundException.class);
        verify(lots, never()).delete(any());
    }

    private static StockLotService.Command command(String purchaseDate) {
        return new StockLotService.Command(B3Ticker.of("PETR4"), 100, Money.brl("38.47"), LocalDate.parse(purchaseDate));
    }

    private static StockLot lot() {
        return lot("PETR4", 1);
    }

    private static StockLot lot(String ticker, long quantity) {
        return StockLot.register(OWNER, B3Ticker.of(ticker), quantity, Money.brl("10.00"),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-26"), NOW);
    }

    private static StockQuote quote(String ticker, String price) {
        return new StockQuote(ticker, Money.brl(price), Instant.parse("2026-11-26T20:00:00Z"), NOW);
    }
}
