package br.com.controlegastos.envelopes.application;

import br.com.controlegastos.envelopes.infrastructure.EnvelopeRepository;
import br.com.controlegastos.income.application.IncomeChangeConstraint;
import br.com.controlegastos.shared.money.Money;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EnvelopeIncomeConstraint implements IncomeChangeConstraint {

    private final EnvelopeRepository envelopes;

    EnvelopeIncomeConstraint(EnvelopeRepository envelopes) {
        this.envelopes = envelopes;
    }

    @Override
    public Money minimumIncomeFor(UUID ownerId, YearMonth month) {
        // month param is kept for interface compatibility; envelopes are not month-specific yet,
        // so we return total base of non-archived envelopes.
        return envelopes.sumBaseAmounts(ownerId);
    }
}
