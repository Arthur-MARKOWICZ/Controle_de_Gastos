package br.com.controlegastos.income.application;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;

public record IncomeSnapshot(Money amount, YearMonth effectiveFrom, Instant changedAt) {
}
