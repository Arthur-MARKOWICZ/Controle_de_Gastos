package br.com.controlegastos.investments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class B3TickerTest {

    @Test
    void normalizesCaseSpacesAndFractionalSuffix() {
        assertThat(B3Ticker.of(" petr4f ").value()).isEqualTo("PETR4");
        assertThat(B3Ticker.of("vale3").value()).isEqualTo("VALE3");
        assertThat(B3Ticker.of("TAEE11F").value()).isEqualTo("TAEE11");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TAEE11", "BOVA11", "MXRF11", "AAPL34", "ITUB4"})
    void acceptsAnyB3AssetFormat(String raw) {
        assertThat(B3Ticker.of(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PETR", "PETR123", "PE4", "PETR-4", "   ", "PETR4FF", "1234A"})
    void rejectsMalformedTickers(String raw) {
        assertThatThrownBy(() -> B3Ticker.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
