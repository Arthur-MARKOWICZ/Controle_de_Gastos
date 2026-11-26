package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.infrastructure.StockLotRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

    private StockLotService service;

    @BeforeEach
    void setUp() {
        service = new StockLotService(lots, authentication, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersLotForCurrentTitularUsingSaoPauloDate() {
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(lots.save(any(StockLot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockLot lot = service.register(command("2026-11-26"));

        assertThat(lot.ownerId()).isEqualTo(OWNER);
        assertThat(lot.createdAt()).isEqualTo(NOW);
        verify(lots).save(lot);
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

        assertThat(service.list()).containsExactly(lot);
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
        return StockLot.register(OWNER, B3Ticker.of("PETR4"), 1, Money.brl("10.00"),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-26"), NOW);
    }
}
