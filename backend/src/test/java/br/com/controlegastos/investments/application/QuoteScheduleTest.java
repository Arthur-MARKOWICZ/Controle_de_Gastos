package br.com.controlegastos.investments.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;
import org.yaml.snakeyaml.Yaml;

/** Cadência padrão das rodadas no application.yml (ADR-0035). */
class QuoteScheduleTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    @DisplayName("rodada do pregão: 16 por dia útil, das 10h00 às 17h30, nenhuma no fim de semana")
    void tradingCronCoversTradingHoursOnly() {
        CronExpression cron = CronExpression.parse(quotesProperty("trading-cron"));

        // Segunda 2026-11-23 até domingo 2026-11-29, em São Paulo.
        List<ZonedDateTime> runs = runsBetween(cron, at(2026, 11, 23, 0, 0), at(2026, 11, 30, 0, 0));

        assertThat(runs).hasSize(16 * 5);
        assertThat(runs).allSatisfy(run -> {
            assertThat(run.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(run.toLocalTime()).isBetween(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(17, 30));
        });
        assertThat(runs.getFirst().toLocalTime()).hasToString("10:00");
        assertThat(runs.get(15).toLocalTime()).hasToString("17:30");
    }

    @Test
    @DisplayName("rodada de faltantes: de hora em hora, aos 15 minutos, todos os dias")
    void missingCronRunsHourly() {
        CronExpression cron = CronExpression.parse(quotesProperty("missing-cron"));

        List<ZonedDateTime> runs = runsBetween(cron, at(2026, 11, 28, 0, 0), at(2026, 11, 29, 0, 0));

        assertThat(runs).hasSize(24).allSatisfy(run -> assertThat(run.getMinute()).isEqualTo(15));
    }

    private static List<ZonedDateTime> runsBetween(CronExpression cron, ZonedDateTime from, ZonedDateTime to) {
        List<ZonedDateTime> runs = new ArrayList<>();
        ZonedDateTime next = cron.next(from.minusSeconds(1));
        while (next != null && next.isBefore(to)) {
            runs.add(next);
            next = cron.next(next);
        }
        return runs;
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(SAO_PAULO);
    }

    @SuppressWarnings("unchecked")
    private static String quotesProperty(String key) {
        try (InputStream in = QuoteScheduleTest.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> app = (Map<String, Object>) root.get("app");
            Map<String, Object> quotes = (Map<String, Object>) ((Map<String, Object>) app.get("investments")).get("quotes");
            return (String) quotes.get(key);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
