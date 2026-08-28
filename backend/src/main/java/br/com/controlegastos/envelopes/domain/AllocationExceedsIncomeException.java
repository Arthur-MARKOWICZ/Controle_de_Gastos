package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;

public final class AllocationExceedsIncomeException extends IllegalArgumentException {

    private final Money excess;

    public AllocationExceedsIncomeException(Money excess) {
        super("A soma das verbas-base excede a renda mensal");
        this.excess = excess;
    }

    public Money excess() {
        return excess;
    }
}
