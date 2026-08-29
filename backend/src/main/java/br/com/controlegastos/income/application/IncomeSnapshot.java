package br.com.controlegastos.income.application;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;
import org.springframework.modulith.NamedInterface;

@NamedInterface("query")
public record IncomeSnapshot(Money amount, YearMonth effectiveFrom, Instant changedAt) {
}
