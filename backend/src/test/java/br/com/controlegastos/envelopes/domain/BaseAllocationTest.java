package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseAllocationTest {

    @Test
    @DisplayName("aceita verba com valor positivo")
    void aceitaValorPositivo() {
        assertThat(new BaseAllocation("verba-1", Money.brl("10.00")).amount()).isEqualTo(Money.brl("10.00"));
    }

    @Test
    @DisplayName("aceita valor zero como borda")
    void aceitaZero() {
        assertThat(new BaseAllocation("verba-1", Money.brl("0.00")).amount().toPlainString()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("recusa valor negativo de um centavo")
    void recusaNegativo() {
        assertThatThrownBy(() -> new BaseAllocation("verba-1", Money.brl("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A verba-base não pode ser negativa");
    }

    @Test
    @DisplayName("recusa verba sem identificador")
    void recusaSemIdentificador() {
        assertThatThrownBy(() -> new BaseAllocation(null, Money.brl("1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A verba é obrigatória");
    }

    @Test
    @DisplayName("recusa identificador em branco")
    void recusaIdentificadorEmBranco() {
        assertThatThrownBy(() -> new BaseAllocation("   ", Money.brl("1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A verba é obrigatória");
    }

    @Test
    @DisplayName("recusa valor ausente")
    void recusaValorAusente() {
        assertThatThrownBy(() -> new BaseAllocation("verba-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O valor-base é obrigatório");
    }

    @Test
    @DisplayName("duas alocações iguais são equivalentes")
    void alocacoesIguaisEquivalentes() {
        assertThat(new BaseAllocation("v", Money.brl("1.00"))).isEqualTo(new BaseAllocation("v", Money.brl("1")));
    }
}
