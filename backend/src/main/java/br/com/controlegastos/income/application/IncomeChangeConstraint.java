package br.com.controlegastos.income.application;

import br.com.controlegastos.shared.money.Money;
import java.time.YearMonth;
import java.util.UUID;

/** Restrição implementável por módulos que reservam parcelas da renda. */
@org.springframework.modulith.NamedInterface("constraints")
@FunctionalInterface
public interface IncomeChangeConstraint {
    Money minimumIncomeFor(UUID ownerId, YearMonth month);
}
