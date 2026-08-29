package br.com.controlegastos.ledger.web;

import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.application.IncomeQuery;
import br.com.controlegastos.ledger.application.LedgerService;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final EnvelopeService envelopes;
    private final LedgerService ledger;
    private final IncomeQuery incomes;
    private final AuthenticationService authentication;

    LedgerController(EnvelopeService envelopes, LedgerService ledger,
                     IncomeQuery incomes, AuthenticationService authentication) {
        this.envelopes = envelopes;
        this.ledger = ledger;
        this.incomes = incomes;
        this.authentication = authentication;
    }

    @GetMapping("/summary")
    SummaryResponse summary(@RequestParam(required = false) String month) {
        YearMonth ym = month == null ? YearMonth.now(EnvelopeService.BUSINESS_ZONE) : parseMonth(month);
        UUID userId = authentication.currentUserId();
        var visible = envelopes.listVisible();
        List<SummaryEnvelope> envelopeViews = visible.stream().map(e -> {
            Money available = ledger.availableFor(e, ym);
            String role = e.ownerId().equals(userId) ? "OWNER" : "PARTICIPANT";
            return new SummaryEnvelope(e.id(), e.ownerId(), e.name(), e.purpose().name(),
                    new MoneyDTO(e.baseAmount().toPlainString(), e.baseAmount().currency()),
                    new MoneyDTO(available.toPlainString(), available.currency()),
                    available.isNegative(), role, e.createdAt(), e.archivedAt(), e.version());
        }).toList();

        Money allocated = visible.stream()
                .map(Envelope::baseAmount)
                .reduce(Money.zero(), Money::add);

        var incomeOpt = incomes.findEffective(userId, ym);
        Money unallocated;
        IncomeDTO incomeDTO = null;
        double usagePct = 0;
        if (incomeOpt.isPresent()) {
            var income = incomeOpt.get();
            Money incomeAmount = income.amount();
            unallocated = incomeAmount.subtract(allocated);
            if (unallocated.isNegative()) unallocated = Money.zero();
            incomeDTO = new IncomeDTO(incomeAmount.toPlainString(), incomeAmount.currency(), ym.toString(), income.changedAt());
            if (!incomeAmount.equals(Money.zero())) {
                usagePct = allocated.amount().doubleValue() / incomeAmount.amount().doubleValue() * 100.0;
            }
        } else {
            unallocated = Money.zero();
        }

        return new SummaryResponse(
                incomeDTO,
                new MoneyDTO(allocated.toPlainString(), allocated.currency()),
                new MoneyDTO(unallocated.toPlainString(), unallocated.currency()),
                usagePct,
                envelopeViews
        );
    }

    private YearMonth parseMonth(String month) {
        try { return YearMonth.parse(month); } catch (DateTimeParseException e) { throw new IllegalArgumentException("Mês inválido"); }
    }

    record MoneyDTO(String amount, String currency) {}
    record IncomeDTO(String amount, String currency, String effectiveFrom, Instant changedAt) {}
    record SummaryEnvelope(UUID id, UUID ownerId, String name, String purpose, MoneyDTO baseAmount, MoneyDTO available, boolean isNegative, String role, Instant createdAt, Instant archivedAt, long version) {}
    record SummaryResponse(IncomeDTO income, MoneyDTO allocated, MoneyDTO unallocated, double usagePct, List<SummaryEnvelope> envelopes) {}
}
