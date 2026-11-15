package br.com.controlegastos.reporting.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
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
                    new ReportEnvelope(LIMIT, "=Mercado", "LIMIT", Money.brl("100.00"), createdAt, null, false),
                    new ReportEnvelope(GOAL, "Viagem", "GOAL", Money.brl("80.00"), createdAt, null, false));
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

    private static final ReportRange JANUARY = new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    private static ReportingService serviceWith(List<LedgerReportingQuery.ReportEnvelope> envelopes,
                                                List<LedgerReportingQuery.ReportEntry> entries, Money closing) {
        return new ReportingService(new LedgerReportingQuery() {
            @Override public List<ReportEnvelope> visibleEnvelopes() { return envelopes; }
            @Override public List<ReportEntry> activeEntries(LocalDate from, LocalDate to) { return entries; }
            @Override public Money availableAt(UUID envelopeId, LocalDate until) { return closing; }
        });
    }

    private static LedgerReportingQuery.ReportEnvelope envelope(UUID id, String name, String purpose, String base,
                                                                boolean wealth) {
        return new LedgerReportingQuery.ReportEnvelope(id, name, purpose, Money.brl(base),
                Instant.parse("2026-01-02T12:00:00Z"), null, wealth);
    }

    @Test
    @DisplayName("nome do arquivo traz o tipo, o período e a extensão do formato")
    void nomeDoArquivo() {
        ReportDocument report = serviceWith(List.of(), List.of(), Money.zero())
                .prepare(ReportType.EXPENSES_BY_PURPOSE, JANUARY, ReportFormat.XLSX);

        assertThat(report.filename()).isEqualTo("gastos-por-tipo_2026-01-01_2026-01-31.xlsx");
    }

    @Test
    @DisplayName("verbas de alocação de patrimônio ficam fora do relatório de consumo")
    void patrimonioForaDoConsumo() {
        UUID wealth = UUID.randomUUID();
        ReportDocument report = serviceWith(List.of(envelope(wealth, "Tesouro", "GOAL", "500.00", true)),
                List.of(new LedgerReportingQuery.ReportEntry(wealth, "EXPENSE", Money.brl("10.00"), LocalDate.of(2026, 1, 5))),
                Money.zero()).prepare(ReportType.EXPENSES_BY_PURPOSE, JANUARY, ReportFormat.CSV);

        assertThat(report.rows()).isEmpty();
    }

    @Test
    @DisplayName("saldo de fechamento zero não conta como limite extrapolado")
    void saldoZeroNaoExtrapola() {
        ReportDocument report = serviceWith(List.of(envelope(LIMIT, "Mercado", "LIMIT", "100.00", false)),
                List.of(), Money.brl("0.00")).prepare(ReportType.LIMIT_EXCEEDED_MONTHS, JANUARY, ReportFormat.CSV);

        assertThat(report.rows()).isEmpty();
    }

    @Test
    @DisplayName("saldo de um centavo negativo já conta como limite extrapolado")
    void umCentavoNegativoExtrapola() {
        ReportDocument report = serviceWith(List.of(envelope(LIMIT, "Mercado", "LIMIT", "100.00", false)),
                List.of(), Money.brl("-0.01")).prepare(ReportType.LIMIT_EXCEEDED_MONTHS, JANUARY, ReportFormat.CSV);

        assertThat(report.rows()).extracting(row -> row.get(5)).containsExactly("-0,01");
    }

    @Test
    @DisplayName("neutraliza nomes iniciados por +, -, @, tabulação e retorno de carro")
    void neutralizaTodosOsPrefixosDeFormula() {
        List<String> names = List.of("+SOMA", "-1", "@cmd", "\tTAB", "\rCR");
        List<LedgerReportingQuery.ReportEnvelope> envelopes = names.stream()
                .map(name -> envelope(UUID.randomUUID(), name, "LIMIT", "1.00", false)).toList();

        ReportDocument report = serviceWith(envelopes, List.of(), Money.zero())
                .prepare(ReportType.EXPENSES_BY_PURPOSE, JANUARY, ReportFormat.CSV);

        assertThat(report.rows()).extracting(row -> row.get(1)).allSatisfy(name -> assertThat(name).startsWith("'"));
    }

    @Test
    @DisplayName("relatório mensal com mês incompleto lança IllegalArgumentException")
    void mensalComMesIncompletoFalha() {
        ReportRange partial = new ReportRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 31));

        assertThatThrownBy(() -> serviceWith(List.of(), List.of(), Money.zero())
                .prepare(ReportType.GOALS_BELOW_TARGET, partial, ReportFormat.CSV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este relatório exige meses completos");
    }

    @Test
    @DisplayName("gastos por tipo aceita período parcial")
    void gastosPorTipoAceitaParcial() {
        ReportRange partial = new ReportRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 20));

        assertThat(serviceWith(List.of(), List.of(), Money.zero())
                .prepare(ReportType.EXPENSES_BY_PURPOSE, partial, ReportFormat.CSV).rows()).isEmpty();
    }

    @Test
    @DisplayName("falha da consulta do ledger propaga a exceção")
    void falhaDoLedgerPropaga() {
        ReportingService failing = new ReportingService(new LedgerReportingQuery() {
            @Override public List<ReportEnvelope> visibleEnvelopes() { throw new IllegalStateException("ledger fora"); }
            @Override public List<ReportEntry> activeEntries(LocalDate from, LocalDate to) { return List.of(); }
            @Override public Money availableAt(UUID envelopeId, LocalDate until) { return Money.zero(); }
        });

        assertThatThrownBy(() -> failing.prepare(ReportType.EXPENSES_BY_PURPOSE, JANUARY, ReportFormat.CSV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger fora");
    }

    @Test
    @DisplayName("meta que atingiu o esperado não aparece como abaixo")
    void metaAtingidaNaoAparece() {
        ReportDocument report = serviceWith(List.of(envelope(GOAL, "Viagem", "GOAL", "80.00", false)),
                List.of(new LedgerReportingQuery.ReportEntry(GOAL, "CONTRIBUTION", Money.brl("80.00"), LocalDate.of(2026, 1, 5))),
                Money.zero()).prepare(ReportType.GOALS_BELOW_TARGET, JANUARY, ReportFormat.CSV);

        assertThat(report.rows()).isEmpty();
    }
}
