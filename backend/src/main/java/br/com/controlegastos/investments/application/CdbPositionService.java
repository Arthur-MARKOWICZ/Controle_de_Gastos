package br.com.controlegastos.investments.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.investments.domain.AnnualRate;
import br.com.controlegastos.investments.domain.CdbPosition;
import br.com.controlegastos.investments.infrastructure.CdbPositionRepository;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posições de CDB prefixado, sempre do titular autenticado (ADR-0034). */
@Service
public class CdbPositionService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final CdbPositionRepository positions;
    private final AuthenticationService authentication;
    private final Clock clock;

    public CdbPositionService(CdbPositionRepository positions, AuthenticationService authentication, Clock clock) {
        this.positions = positions;
        this.authentication = authentication;
        this.clock = clock;
    }

    public record Command(Money principal, AnnualRate annualRate, LocalDate startDate, LocalDate maturityDate) {
    }

    @Transactional
    public CdbPositionView register(Command command) {
        LocalDate today = todayInSaoPaulo();
        CdbPosition position = CdbPosition.register(
                authentication.currentUserId(),
                command.principal(),
                command.annualRate(),
                command.startDate(),
                command.maturityDate(),
                today,
                clock.instant());
        return CdbPositionView.of(positions.save(position), today);
    }

    @Transactional(readOnly = true)
    public List<CdbPositionView> list() {
        LocalDate today = todayInSaoPaulo();
        return positions.findListing(authentication.currentUserId()).stream()
                .map(position -> CdbPositionView.of(position, today))
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        CdbPosition position = positions.findByIdAndOwnerId(id, authentication.currentUserId())
                .orElseThrow(CdbPositionNotFoundException::new);
        positions.delete(position);
    }

    private LocalDate todayInSaoPaulo() {
        return LocalDate.now(clock.withZone(SAO_PAULO));
    }
}
