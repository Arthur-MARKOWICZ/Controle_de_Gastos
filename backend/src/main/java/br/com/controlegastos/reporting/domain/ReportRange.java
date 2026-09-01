package br.com.controlegastos.reporting.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ReportRange(LocalDate from, LocalDate to) {

    public ReportRange {
        Objects.requireNonNull(from, "A data inicial é obrigatória");
        Objects.requireNonNull(to, "A data final é obrigatória");
        if (from.isAfter(to)) throw new IllegalArgumentException("O intervalo de datas é inválido");
    }

    public void requireWholeMonths() {
        if (from.getDayOfMonth() != 1 || to.getDayOfMonth() != to.lengthOfMonth()) {
            throw new IllegalArgumentException("Este relatório exige meses completos");
        }
    }

    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            months.add(month);
        }
        return List.copyOf(months);
    }
}
