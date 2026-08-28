package br.com.controlegastos.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void keepsExactDecimalArithmeticAndCanonicalScale() {
        Money total = Money.brl("0.10").add(Money.brl(new BigDecimal("0.20")));

        assertThat(total.amount()).isEqualByComparingTo("0.30");
        assertThat(total.toPlainString()).isEqualTo("0.30");
        assertThat(total.currency()).isEqualTo("BRL");
    }

    @Test
    void rejectsImplicitRoundingAndValuesBeyondDatabasePrecision() {
        assertThatThrownBy(() -> Money.brl("10.999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duas casas");
        assertThatThrownBy(() -> Money.brl("100000000000000000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void supportsNegativeBalancesWithoutLosingExactness() {
        Money balance = Money.brl("10.00").subtract(Money.brl("12.50"));

        assertThat(balance.toPlainString()).isEqualTo("-2.50");
        assertThat(balance.isNegative()).isTrue();
    }

    @Test
    void doesNotExposePrimitiveNumericFactoriesForMoney() {
        assertThat(Arrays.stream(Money.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("brl"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .noneMatch(Class::isPrimitive);
    }
}
