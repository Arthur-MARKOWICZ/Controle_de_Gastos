package br.com.controlegastos.reporting.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportRangeTest {

    @Test
    void rejectsAnInvertedDateRange() {
        assertThatThrownBy(() -> new ReportRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresCompleteMonthsForMonthlyReports() {
        var partial = new ReportRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 31));

        assertThatThrownBy(partial::requireWholeMonths)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
