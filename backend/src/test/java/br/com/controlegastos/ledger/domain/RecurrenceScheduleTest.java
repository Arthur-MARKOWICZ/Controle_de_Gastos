package br.com.controlegastos.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecurrenceScheduleTest {

    @ParameterizedTest(name = "dia {0} em {1} vence em {2}")
    @CsvSource({
            "5, 2026-12, 2026-12-05",
            "31, 2027-02, 2027-02-28",
            "31, 2028-02, 2028-02-29",
            "31, 2027-04, 2027-04-30",
            "30, 2027-02, 2027-02-28",
            "31, 2027-01, 2027-01-31",
    })
    void dueDateClampsToLastDayOfShortMonths(int day, String month, String expected) {
        assertThat(new RecurrenceSchedule(day).dueDate(YearMonth.parse(month))).isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 32})
    void rejectsDaysOutsideOneToThirtyOne(int day) {
        assertThatThrownBy(() -> new RecurrenceSchedule(day)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "dia {0} criado em {1} começa em {2}")
    @CsvSource({
            "5, 2026-12-01, 2026-12",
            "5, 2026-12-05, 2026-12",
            "5, 2026-12-20, 2027-01",
            "31, 2027-04-30, 2027-04",
            "31, 2027-02-28, 2027-02",
            "10, 2026-12-31, 2027-01",
    })
    void firstCompetenceIsCurrentMonthUntilTheDueDay(int day, String today, String expected) {
        assertThat(new RecurrenceSchedule(day).firstCompetence(LocalDate.parse(today))).isEqualTo(YearMonth.parse(expected));
    }

    @Test
    @DisplayName("competência atual é devida a partir do dia previsto")
    void currentCompetenceIsDueOnAndAfterDueDay() {
        RecurrenceSchedule schedule = new RecurrenceSchedule(5);
        YearMonth first = YearMonth.of(2026, 12);

        assertThat(schedule.dueCompetence(first, LocalDate.of(2026, 12, 4))).isEmpty();
        assertThat(schedule.dueCompetence(first, LocalDate.of(2026, 12, 5))).contains(YearMonth.of(2026, 12));
        assertThat(schedule.dueCompetence(first, LocalDate.of(2026, 12, 9))).contains(YearMonth.of(2026, 12));
    }

    @Test
    @DisplayName("nada é devido antes da primeira competência")
    void nothingIsDueBeforeTheFirstCompetence() {
        RecurrenceSchedule schedule = new RecurrenceSchedule(5);

        assertThat(schedule.dueCompetence(YearMonth.of(2027, 1), LocalDate.of(2026, 12, 21))).isEmpty();
    }

    @Test
    @DisplayName("competências passadas nunca são devidas: só o mês corrente")
    void onlyTheCurrentMonthCanBeDue() {
        RecurrenceSchedule schedule = new RecurrenceSchedule(5);

        assertThat(schedule.dueCompetence(YearMonth.of(2026, 10), LocalDate.of(2026, 12, 2))).isEmpty();
        assertThat(schedule.dueCompetence(YearMonth.of(2026, 10), LocalDate.of(2026, 12, 5))).contains(YearMonth.of(2026, 12));
    }

    @Test
    @DisplayName("dia 31 em fevereiro é devido em 28/02")
    void day31IsDueOnLastDayOfFebruary() {
        RecurrenceSchedule schedule = new RecurrenceSchedule(31);

        assertThat(schedule.dueCompetence(YearMonth.of(2027, 1), LocalDate.of(2027, 2, 27))).isEmpty();
        assertThat(schedule.dueCompetence(YearMonth.of(2027, 1), LocalDate.of(2027, 2, 28))).contains(YearMonth.of(2027, 2));
    }
}
