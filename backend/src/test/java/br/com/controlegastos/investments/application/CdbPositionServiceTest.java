package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.AnnualRate;
import br.com.controlegastos.investments.domain.CdbPosition;
import br.com.controlegastos.investments.infrastructure.CdbPositionRepository;
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
class CdbPositionServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    /** 2026-11-27 em UTC, mas ainda 2026-11-26 (23h) em America/Sao_Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-27T02:00:00Z");

    @Mock CdbPositionRepository positions;
    @Mock AuthenticationService authentication;

    private CdbPositionService service;

    @BeforeEach
    void setUp() {
        service = new CdbPositionService(positions, authentication, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersPositionForCurrentTitularUsingSaoPauloDate() {
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(positions.save(any(CdbPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CdbPositionView view = service.register(command("2025-11-26", "2027-11-26"));

        assertThat(view.position().ownerId()).isEqualTo(OWNER);
        assertThat(view.position().createdAt()).isEqualTo(NOW);
        assertThat(view.estimatedValue()).isEqualTo(Money.brl("11250.00"));
        verify(positions).save(view.position());
    }

    @Test
    void rejectsStartAfterTodayInSaoPaulo() {
        when(authentication.currentUserId()).thenReturn(OWNER);

        assertThatThrownBy(() -> service.register(command("2026-11-27", "2027-11-26")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(positions, never()).save(any());
    }

    @Test
    void listsOnlyCurrentTitularPositionsWithRecalculatedEstimate() {
        CdbPosition position = position("2025-11-26", "2027-11-26");
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(positions.findListing(OWNER)).thenReturn(List.of(position));

        List<CdbPositionView> listing = service.list();

        assertThat(listing).hasSize(1);
        assertThat(listing.getFirst().position()).isSameAs(position);
        assertThat(listing.getFirst().estimatedValue()).isEqualTo(Money.brl("11250.00"));
    }

    @Test
    void deletesOwnPosition() {
        CdbPosition position = position("2025-11-26", "2027-11-26");
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(positions.findByIdAndOwnerId(position.id(), OWNER)).thenReturn(Optional.of(position));

        service.delete(position.id());

        verify(positions).delete(position);
    }

    @Test
    void deletingUnknownOrForeignPositionIsNotFound() {
        UUID id = UUID.randomUUID();
        when(authentication.currentUserId()).thenReturn(OWNER);
        when(positions.findByIdAndOwnerId(id, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(CdbPositionNotFoundException.class);
        verify(positions, never()).delete(any());
    }

    private static CdbPositionService.Command command(String start, String maturity) {
        return new CdbPositionService.Command(
                Money.brl("10000.00"), AnnualRate.of("0.1250"),
                LocalDate.parse(start), LocalDate.parse(maturity));
    }

    private static CdbPosition position(String start, String maturity) {
        return CdbPosition.register(OWNER, Money.brl("10000.00"), AnnualRate.of("0.1250"),
                LocalDate.parse(start), LocalDate.parse(maturity),
                LocalDate.parse("2026-11-26"), NOW);
    }
}
