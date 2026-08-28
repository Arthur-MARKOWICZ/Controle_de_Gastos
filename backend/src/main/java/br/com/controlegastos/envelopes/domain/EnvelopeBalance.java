package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import java.util.Objects;

public record EnvelopeBalance(Money available) {

    public EnvelopeBalance {
        Objects.requireNonNull(available, "O saldo é obrigatório");
    }

    public static EnvelopeBalance startWith(Money openingBalance) {
        return new EnvelopeBalance(openingBalance);
    }

    public EnvelopeBalance allocate(Money amount) {
        requireNonNegative(amount, "O aporte");
        return new EnvelopeBalance(available.add(amount));
    }

    public EnvelopeBalance spend(Money amount) {
        requireNonNegative(amount, "O gasto");
        return new EnvelopeBalance(available.subtract(amount));
    }

    public boolean isNegative() {
        return available.isNegative();
    }

    private static void requireNonNegative(Money amount, String field) {
        Objects.requireNonNull(amount, field + " é obrigatório");
        if (amount.isNegative()) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
    }
}
