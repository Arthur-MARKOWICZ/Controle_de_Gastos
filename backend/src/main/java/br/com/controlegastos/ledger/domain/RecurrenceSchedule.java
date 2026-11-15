package br.com.controlegastos.ledger.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/** Regras de data de uma recorrência mensal (ADR-0031). */
public record RecurrenceSchedule(int dayOfMonth) {

    public RecurrenceSchedule {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("O dia deve estar entre 1 e 31");
        }
    }

    /** Dia previsto na competência; dias inexistentes viram o último dia do mês. */
    public LocalDate dueDate(YearMonth competence) {
        return competence.atDay(Math.min(dayOfMonth, competence.lengthOfMonth()));
    }

    /** Mês corrente se o dia previsto ainda não passou; senão, o próximo. */
    public YearMonth firstCompetence(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        return dueDate(current).isBefore(today) ? current.plusMonths(1) : current;
    }

    /** Só a competência corrente pode ser devida: meses passados não são preenchidos. */
    public Optional<YearMonth> dueCompetence(YearMonth firstCompetence, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (current.isBefore(firstCompetence) || dueDate(current).isAfter(today)) {
            return Optional.empty();
        }
        return Optional.of(current);
    }
}
