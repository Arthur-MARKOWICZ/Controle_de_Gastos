package br.com.controlegastos.investments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AnnualRateTest {

    @Test
    void acceptsPositiveDecimalWithFourPlaces() {
        assertThat(AnnualRate.of("0.1250").value()).isEqualByComparingTo(new BigDecimal("0.1250"));
        assertThat(AnnualRate.of("0.1250").toPlainString()).isEqualTo("0.1250");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12.50", "0.125", "0.12500", "0.0000", "-0.0100", "   "})
    void rejectsPercentStyleWrongScaleZeroNegativeBlankAndNull(String raw) {
        assertThatThrownBy(() -> AnnualRate.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
