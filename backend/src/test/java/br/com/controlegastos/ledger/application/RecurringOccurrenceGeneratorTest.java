package br.com.controlegastos.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.ledger.domain.RecurringEntry;
import br.com.controlegastos.ledger.infrastructure.RecurringEntryRepository;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository;
import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RecurringOccurrenceGeneratorTest {

    private final RecurringEntryRepository entries = mock(RecurringEntryRepository.class);
    private final RecurringOccurrenceRepository occurrences = mock(RecurringOccurrenceRepository.class);

    /** 12:00 em America/Sao_Paulo no dia informado. */
    private RecurringOccurrenceGenerator generatorOn(String date) {
        Instant noonBrt = LocalDate.parse(date).atTime(15, 0).toInstant(ZoneOffset.UTC);
        return new RecurringOccurrenceGenerator(entries, occurrences, Clock.fixed(noonBrt, ZoneOffset.UTC));
    }

    private RecurringEntry recurrence(int day, String createdOn, String amount, String description) {
        return RecurringEntry.create(UUID.randomUUID(), LedgerKind.EXPENSE, Money.brl(amount), day, description,
                LocalDate.parse(createdOn), Instant.parse(createdOn + "T12:00:00Z"));
    }

    @Test
    @DisplayName("gera no dia previsto com competência e valor sugerido")
    void generatesOnDueDay() {
        RecurringEntry rent = recurrence(5, "2026-11-01", "1800.00", "Aluguel");
        when(entries.findGenerationCandidates(LocalDate.of(2026, 12, 1))).thenReturn(List.of(rent));

        generatorOn("2026-12-05").generateDue();

        verify(occurrences).insertIfAbsent(any(UUID.class), eq(rent.id()), eq(rent.envelopeId()),
                eq(LocalDate.of(2026, 12, 1)), eq(LocalDate.of(2026, 12, 5)), eq(new BigDecimal("1800.00")), any(Instant.class));
    }

    @Test
    @DisplayName("antes do dia previsto não gera")
    void doesNotGenerateBeforeDueDay() {
        RecurringEntry rent = recurrence(5, "2026-11-01", "1800.00", null);
        when(entries.findGenerationCandidates(LocalDate.of(2026, 12, 1))).thenReturn(List.of(rent));

        generatorOn("2026-12-04").generateDue();

        verify(occurrences, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recupera dias perdidos dentro do mês, com a data prevista original")
    void recoversMissedDaysWithinTheMonth() {
        RecurringEntry rent = recurrence(5, "2026-11-01", "1800.00", null);
        when(entries.findGenerationCandidates(LocalDate.of(2026, 12, 1))).thenReturn(List.of(rent));

        generatorOn("2026-12-09").generateDue();

        verify(occurrences).insertIfAbsent(any(UUID.class), eq(rent.id()), any(), eq(LocalDate.of(2026, 12, 1)),
                eq(LocalDate.of(2026, 12, 5)), any(), any());
    }

    @Test
    @DisplayName("dia 31 em fevereiro gera em 28/02")
    void day31InFebruary() {
        RecurringEntry rent = recurrence(31, "2027-01-02", "1800.00", null);
        when(entries.findGenerationCandidates(LocalDate.of(2027, 2, 1))).thenReturn(List.of(rent));

        generatorOn("2027-02-28").generateDue();

        verify(occurrences).insertIfAbsent(any(UUID.class), eq(rent.id()), any(), eq(LocalDate.of(2027, 2, 1)),
                eq(LocalDate.of(2027, 2, 28)), any(), any());
    }

    @Test
    @DisplayName("primeira competência futura não gera no mês corrente")
    void futureFirstCompetenceSkipsCurrentMonth() {
        RecurringEntry rent = recurrence(5, "2026-12-20", "1800.00", null); // começa em 2027-01
        when(entries.findGenerationCandidates(any())).thenReturn(List.of(rent));

        generatorOn("2026-12-21").generateDue();

        verify(occurrences, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("log da geração tem só contagens: sem valores, descrições ou IDs de pessoas")
    void logsOnlyCounts(CapturedOutput output) {
        RecurringEntry rent = recurrence(5, "2026-11-01", "1834.56", "Aluguel do apartamento");
        when(entries.findGenerationCandidates(any())).thenReturn(List.of(rent));
        when(occurrences.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        generatorOn("2026-12-05").generateDue();

        assertThat(output.getOut()).contains("1 ocorrência(s) criada(s) de 1 recorrência(s)");
        assertThat(output.getOut()).doesNotContain("1834.56").doesNotContain("Aluguel do apartamento");
    }

    @Test
    @DisplayName("falha na geração do arranque não derruba a aplicação e não vaza dados")
    void startupFailureIsLoggedNotThrown(CapturedOutput output) {
        when(entries.findGenerationCandidates(any())).thenThrow(new IllegalStateException("relation does not exist"));

        org.assertj.core.api.Assertions.assertThatCode(() -> generatorOn("2026-12-05").generateOnStartup())
                .doesNotThrowAnyException();

        assertThat(output.getOut()).contains("Geração de recorrências no arranque falhou");
    }
}
