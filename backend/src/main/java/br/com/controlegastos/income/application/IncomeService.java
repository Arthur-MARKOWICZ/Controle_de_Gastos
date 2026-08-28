package br.com.controlegastos.income.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.domain.IncomeRevision;
import br.com.controlegastos.income.domain.MonthlyIncome;
import br.com.controlegastos.income.domain.MonthlyIncomeId;
import br.com.controlegastos.income.infrastructure.IncomeRevisionRepository;
import br.com.controlegastos.income.infrastructure.MonthlyIncomeRepository;
import br.com.controlegastos.income.domain.IncomeBelowAllocationsException;
import br.com.controlegastos.income.domain.IncomeNotConfiguredException;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final MonthlyIncomeRepository incomes;
    private final IncomeRevisionRepository revisions;
    private final AuthenticationService authentication;
    private final Clock clock;
    private final List<IncomeChangeConstraint> constraints;

    public IncomeService(MonthlyIncomeRepository incomes, IncomeRevisionRepository revisions,
                         AuthenticationService authentication, Clock clock, List<IncomeChangeConstraint> constraints) {
        this.incomes = incomes;
        this.revisions = revisions;
        this.authentication = authentication;
        this.clock = clock;
        this.constraints = List.copyOf(constraints);
    }

    @Transactional
    public IncomeSnapshot change(Money requestedAmount) {
        Objects.requireNonNull(requestedAmount, "A renda é obrigatória");
        UUID ownerId = authentication.currentUserId();
        Instant now = clock.instant();
        YearMonth month = YearMonth.from(now.atZone(BUSINESS_ZONE));
        requireAllowedAmount(ownerId, month, requestedAmount);

        MonthlyIncomeId id = new MonthlyIncomeId(ownerId, month);
        var existing = incomes.findById(id);
        MonthlyIncome income = existing
                .orElseGet(() -> MonthlyIncome.start(ownerId, month, requestedAmount, now));

        boolean isNew = existing.isEmpty();
        boolean changed = isNew || income.changeTo(requestedAmount, now);
        if (!changed) {
            return income.snapshot();
        }

        MonthlyIncome saved = incomes.save(income);
        revisions.save(IncomeRevision.record(
                ownerId, ownerId, requestedAmount, month, now));
        return saved.snapshot();
    }

    @Transactional(readOnly = true)
    public IncomeSnapshot findCurrent() {
        return find(YearMonth.from(clock.instant().atZone(BUSINESS_ZONE)));
    }

    @Transactional(readOnly = true)
    public IncomeSnapshot find(YearMonth month) {
        Objects.requireNonNull(month, "O mês é obrigatório");
        return incomes.findEffectiveAtOrBefore(authentication.currentUserId(), month)
                .map(MonthlyIncome::snapshot)
                .orElseThrow(() -> new IncomeNotConfiguredException(month));
    }

    @Transactional(readOnly = true)
    public IncomeHistoryPage history(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("A paginação deve usar page >= 0 e size entre 1 e 100");
        }
        var result = revisions.findByOwnerIdOrderByChangedAtDescIdDesc(
                authentication.currentUserId(), PageRequest.of(page, size));
        return new IncomeHistoryPage(
                result.getContent().stream().map(IncomeHistoryEntry::from).toList(),
                page,
                size,
                result.hasNext()
        );
    }

    private void requireAllowedAmount(UUID ownerId, YearMonth month, Money requestedAmount) {
        if (requestedAmount.isNegative()) {
            throw new IllegalArgumentException("A renda não pode ser negativa");
        }
        Money requiredMinimum = constraints.stream()
                .map(constraint -> constraint.minimumIncomeFor(ownerId, month))
                .filter(Objects::nonNull)
                .max(Money::compareTo)
                .orElse(Money.zero());
        if (requestedAmount.compareTo(requiredMinimum) < 0) {
            throw new IncomeBelowAllocationsException(requestedAmount, requiredMinimum);
        }
    }
}
