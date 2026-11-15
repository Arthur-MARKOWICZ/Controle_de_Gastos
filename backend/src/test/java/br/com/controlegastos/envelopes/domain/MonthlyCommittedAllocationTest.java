package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonthlyCommittedAllocationTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void usesBaseAmountForOrdinaryEnvelopes() {
        Envelope limit = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("400.00"), CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(limit, YearMonth.of(2026, 9)))
                .isEqualTo(Money.brl("400.00"));
    }

    @Test
    void includesMonthlyAnnualInstallmentForTheMonth() {
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "IPVA", Money.brl("1000.01"), MonthDay.of(1, 10), AnnualExpenseFundingMode.MONTHLY, CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(annual, YearMonth.of(2026, 9)))
                .isEqualTo(Money.brl("200.00"));
    }

    @Test
    void ignoresOneTimeAnnualAsMonthlyBase() {
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "Seguro", Money.brl("700.00"), MonthDay.of(11, 20), AnnualExpenseFundingMode.ONE_TIME, CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(annual, YearMonth.of(2026, 9)))
                .isEqualTo(Money.zero());
    }

    @Test
    void sumsCommittedAllocationsAcrossEnvelopes() {
        Envelope limit = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("400.00"), CREATED);
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "IPVA", Money.brl("1000.01"), MonthDay.of(1, 10), AnnualExpenseFundingMode.MONTHLY, CREATED);

        assertThat(MonthlyCommittedAllocation.totalFor(List.of(limit, annual), YearMonth.of(2026, 9)))
                .isEqualTo(Money.brl("600.00"));
    }

    @Test
    @DisplayName("verba arquivada não compromete a renda do mês")
    void arquivadaNaoCompromete() {
        Envelope limit = Envelope.create(OWNER, "Antiga", EnvelopePurpose.LIMIT, Money.brl("400.00"), CREATED);
        limit.archive(CREATED.plusSeconds(60));

        assertThat(MonthlyCommittedAllocation.amountFor(limit, YearMonth.of(2026, 9))).isEqualTo(Money.zero());
    }

    @Test
    @DisplayName("gasto anual mensal não compromete meses depois do vencimento do ciclo atual")
    void mesForaDoCicloNaoTemParcelaAntesDeCriar() {
        Envelope annual = Envelope.createAnnualExpense(
                OWNER, "IPVA", Money.brl("1000.01"), MonthDay.of(1, 10), AnnualExpenseFundingMode.MONTHLY, CREATED);

        assertThat(MonthlyCommittedAllocation.amountFor(annual, YearMonth.of(2026, 8))).isEqualTo(Money.zero());
    }

    @Test
    @DisplayName("lista vazia soma zero com duas casas")
    void listaVaziaSomaZero() {
        assertThat(MonthlyCommittedAllocation.totalFor(List.of(), YearMonth.of(2026, 9)).toPlainString()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("verba ausente lança NullPointerException com mensagem")
    void verbaAusenteFalha() {
        assertThatThrownBy(() -> MonthlyCommittedAllocation.amountFor(null, YearMonth.of(2026, 9)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A verba é obrigatória");
    }

    @Test
    @DisplayName("mês ausente lança NullPointerException com mensagem")
    void mesAusenteFalha() {
        Envelope limit = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED);

        assertThatThrownBy(() -> MonthlyCommittedAllocation.amountFor(limit, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O mês é obrigatório");
    }

    @Test
    @DisplayName("lista de verbas ausente lança NullPointerException com mensagem")
    void listaAusenteFalha() {
        assertThatThrownBy(() -> MonthlyCommittedAllocation.totalFor(null, YearMonth.of(2026, 9)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("As verbas são obrigatórias");
    }
}
