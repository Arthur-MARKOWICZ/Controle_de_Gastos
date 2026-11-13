package br.com.controlegastos.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("intervalo de um dia é válido")
    void intervaloDeUmDia() {
        assertThat(new ReportRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1)).months())
                .containsExactly(YearMonth.of(2026, 3));
    }

    @Test
    @DisplayName("lista os meses do intervalo atravessando a virada do ano")
    void mesesAtravessandoAno() {
        assertThat(new ReportRange(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 2, 28)).months())
                .containsExactly(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    @DisplayName("aceita fevereiro completo em ano bissexto")
    void fevereiroBissexto() {
        new ReportRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)).requireWholeMonths();
    }

    @Test
    @DisplayName("recusa mês terminando antes do último dia com mensagem")
    void mesIncompletoNoFim() {
        assertThatThrownBy(new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30))::requireWholeMonths)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este relatório exige meses completos");
    }

    @Test
    @DisplayName("data inicial ausente lança NullPointerException com mensagem")
    void dataInicialAusente() {
        assertThatThrownBy(() -> new ReportRange(null, LocalDate.of(2026, 1, 31)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A data inicial é obrigatória");
    }

    @Test
    @DisplayName("data final ausente lança NullPointerException com mensagem")
    void dataFinalAusente() {
        assertThatThrownBy(() -> new ReportRange(LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A data final é obrigatória");
    }

    @Test
    @DisplayName("a lista de meses é imutável")
    void listaImutavel() {
        var months = new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).months();
        assertThatThrownBy(() -> months.add(YearMonth.of(2026, 2))).isInstanceOf(UnsupportedOperationException.class);
    }
}
