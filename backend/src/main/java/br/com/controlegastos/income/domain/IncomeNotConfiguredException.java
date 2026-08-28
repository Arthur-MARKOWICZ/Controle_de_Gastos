package br.com.controlegastos.income.domain;

import java.time.YearMonth;

public final class IncomeNotConfiguredException extends RuntimeException {
    private final YearMonth month;

    public IncomeNotConfiguredException(YearMonth month) {
        super("Nenhuma renda foi configurada para o mês informado");
        this.month = month;
    }

    public YearMonth month() {
        return month;
    }
}
