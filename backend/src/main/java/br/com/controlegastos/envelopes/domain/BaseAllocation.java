package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import java.util.Objects;

public record BaseAllocation(String envelopeId, Money amount) {

    public BaseAllocation {
        if (envelopeId == null || envelopeId.isBlank()) {
            throw new IllegalArgumentException("A verba é obrigatória");
        }
        Objects.requireNonNull(amount, "O valor-base é obrigatório");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("A verba-base não pode ser negativa");
        }
    }
}
