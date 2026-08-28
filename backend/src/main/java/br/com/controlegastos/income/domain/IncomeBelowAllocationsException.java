package br.com.controlegastos.income.domain;

import br.com.controlegastos.shared.money.Money;

public final class IncomeBelowAllocationsException extends RuntimeException {
    private final Money requiredMinimum;
    private final Money shortfall;

    public IncomeBelowAllocationsException(Money requested, Money requiredMinimum) {
        super("A renda informada é menor que a soma das verbas-base");
        this.requiredMinimum = requiredMinimum;
        this.shortfall = requiredMinimum.subtract(requested);
    }

    public Money requiredMinimum() {
        return requiredMinimum;
    }

    public Money shortfall() {
        return shortfall;
    }
}
