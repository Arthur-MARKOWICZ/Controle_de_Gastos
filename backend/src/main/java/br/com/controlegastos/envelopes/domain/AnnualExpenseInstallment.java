package br.com.controlegastos.envelopes.domain;

import br.com.controlegastos.shared.money.Money;
import java.time.YearMonth;

public record AnnualExpenseInstallment(YearMonth month, Money amount) {
}
