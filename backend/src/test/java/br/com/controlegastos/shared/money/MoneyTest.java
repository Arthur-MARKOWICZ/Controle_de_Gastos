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
    void multipliesByPositiveWholeQuantityKeepingTwoDecimals() {
        assertThat(Money.brl("38.47").multiply(100).toPlainString()).isEqualTo("3847.00");
        assertThat(Money.brl("0.01").multiply(3).toPlainString()).isEqualTo("0.03");
    }

    @Test
    void rejectsNonPositiveQuantityAndProductsBeyondDatabasePrecision() {
        assertThatThrownBy(() -> Money.brl("10.00").multiply(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.brl("10.00").multiply(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.brl("99999999999999999.00").multiply(10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void doesNotExposePrimitiveNumericFactoriesForMoney() {
        assertThat(Arrays.stream(Money.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("brl"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .noneMatch(Class::isPrimitive);
    }
}
