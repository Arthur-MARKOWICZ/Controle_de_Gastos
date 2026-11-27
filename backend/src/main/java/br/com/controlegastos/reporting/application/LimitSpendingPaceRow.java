package br.com.controlegastos.reporting.application;

import java.time.LocalDate;
import java.util.UUID;

/** Linha JSON de projeção de ritmo para uma verba LIMIT. */
public record LimitSpendingPaceRow(
        UUID envelopeId,
        String name,
        String capacity,
        String spent,
        String projectedMonthSpend,
        String dailyRemainingAllowance,
        LocalDate projectedOverrunDay,
        String month) {
}
