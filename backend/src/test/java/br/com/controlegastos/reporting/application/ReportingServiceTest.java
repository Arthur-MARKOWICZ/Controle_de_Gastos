package br.com.controlegastos.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.ledger.application.LedgerReportingQuery;
import br.com.controlegastos.reporting.domain.ReportRange;
import br.com.controlegastos.shared.money.Money;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportingServiceTest {

    private static final UUID LIMIT = UUID.randomUUID();
    private static final UUID GOAL = UUID.randomUUID();

    @Test
    void createsCsvWithOneExpenseRowPerEnvelopeAndNeutralizesSpreadsheetFormulas() throws Exception {
        ReportingService service = new ReportingService(new FakeLedgerQuery());

        ReportDocument report = service.prepare(ReportType.EXPENSES_BY_PURPOSE,
                new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), ReportFormat.CSV);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        report.writeTo(output);

        String csv = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFtipo;verba;gastos_brl");
        assertThat(csv).contains("LIMIT;'=Mercado;20,00;1;20,00");
        assertThat(csv).contains("GOAL;Viagem;0,00;0;0,00");
    }

    @Test
    void reportsNegativeLimitBalancesAndGoalsBelowExplicitContributions() {
        ReportingService service = new ReportingService(new FakeLedgerQuery());
        ReportRange range = new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        ReportDocument limits = service.prepare(ReportType.LIMIT_EXCEEDED_MONTHS, range, ReportFormat.CSV);
        ReportDocument goals = service.prepare(ReportType.GOALS_BELOW_TARGET, range, ReportFormat.CSV);

        assertThat(limits.rows()).containsExactly(List.of("2026-01", "'=Mercado", "100,00", "0,00", "20,00", "-5,00"));
        assertThat(goals.rows()).containsExactly(List.of("Viagem", "1", "80,00", "30,00", "50,00"));
    }

    private static class FakeLedgerQuery implements LedgerReportingQuery {
        @Override
        public List<ReportEnvelope> visibleEnvelopes() {
            Instant createdAt = Instant.parse("2026-01-02T12:00:00Z");
            return List.of(
                    new ReportEnvelope(LIMIT, "=Mercado", "LIMIT", Money.brl("100.00"), createdAt, null),
                    new ReportEnvelope(GOAL, "Viagem", "GOAL", Money.brl("80.00"), createdAt, null));
        }

        @Override
        public List<ReportEntry> activeEntries(LocalDate from, LocalDate to) {
            return List.of(
                    new ReportEntry(LIMIT, "EXPENSE", Money.brl("20.00"), LocalDate.of(2026, 1, 10)),
                    new ReportEntry(GOAL, "CONTRIBUTION", Money.brl("30.00"), LocalDate.of(2026, 1, 11)));
        }

        @Override
        public Money availableAt(UUID envelopeId, LocalDate until) {
            return envelopeId.equals(LIMIT) ? Money.brl("-5.00") : Money.brl("0.00");
        }
    }
}
