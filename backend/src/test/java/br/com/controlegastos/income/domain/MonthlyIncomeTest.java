package br.com.controlegastos.income.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlyIncomeTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private final UUID owner = UUID.randomUUID();

    @Test
    @DisplayName("inicia a renda do mês com valor e instante informados")
    void iniciaRenda() {
        MonthlyIncome income = MonthlyIncome.start(owner, MONTH, Money.brl("5000.00"), NOW);

        assertThat(income.amount()).isEqualTo(Money.brl("5000.00"));
        assertThat(income.snapshot().effectiveFrom()).isEqualTo(MONTH);
        assertThat(income.snapshot().changedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("renda zero é aceita como borda válida")
    void rendaZeroAceita() {
        assertThat(MonthlyIncome.start(owner, MONTH, Money.brl("0.00"), NOW).amount().toPlainString()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("alterar para outro valor atualiza e informa que mudou")
    void alterarParaOutroValor() {
        MonthlyIncome income = MonthlyIncome.start(owner, MONTH, Money.brl("5000.00"), NOW);

        assertThat(income.changeTo(Money.brl("5000.01"), NOW.plusSeconds(60))).isTrue();
        assertThat(income.snapshot().changedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("alterar para o mesmo valor com outra escala não registra mudança")
    void mesmoValorNaoMuda() {
        MonthlyIncome income = MonthlyIncome.start(owner, MONTH, Money.brl("5000.00"), NOW);

        assertThat(income.changeTo(Money.brl("5000"), NOW.plusSeconds(60))).isFalse();
        assertThat(income.snapshot().changedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("renda negativa no início lança IllegalArgumentException")
    void rendaNegativaFalha() {
        assertThatThrownBy(() -> MonthlyIncome.start(owner, MONTH, Money.brl("-0.01"), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A renda não pode ser negativa");
    }

    @Test
    @DisplayName("alterar para renda negativa lança IllegalArgumentException e mantém o valor")
    void alterarParaNegativaFalha() {
        MonthlyIncome income = MonthlyIncome.start(owner, MONTH, Money.brl("100.00"), NOW);

        assertThatThrownBy(() -> income.changeTo(Money.brl("-1.00"), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A renda não pode ser negativa");
        assertThat(income.amount()).isEqualTo(Money.brl("100.00"));
    }

    @Test
    @DisplayName("renda ausente lança NullPointerException com mensagem")
    void rendaAusenteFalha() {
        assertThatThrownBy(() -> MonthlyIncome.start(owner, MONTH, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A renda é obrigatória");
    }

    @Test
    @DisplayName("instante ausente lança NullPointerException com mensagem")
    void instanteAusenteFalha() {
        assertThatThrownBy(() -> MonthlyIncome.start(owner, MONTH, Money.brl("1.00"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O instante é obrigatório");
    }
}
