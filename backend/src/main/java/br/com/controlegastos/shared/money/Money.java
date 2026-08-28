package br.com.controlegastos.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Valor monetário decimal e imutável, restrito a BRL no primeiro corte. */
public final class Money implements Comparable<Money> {

    public static final int PRECISION = 19;
    public static final int SCALE = 2;
    private static final String CURRENCY = "BRL";
    private static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = normalize(amount);
    }

    public static Money brl(String amount) {
        Objects.requireNonNull(amount, "O valor monetário é obrigatório");
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("O valor monetário deve ser um decimal válido", exception);
        }
    }

    public static Money brl(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return ZERO;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return CURRENCY;
    }

    public String toPlainString() {
        return amount.toPlainString();
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "O valor a somar é obrigatório");
        return brl(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "O valor a subtrair é obrigatório");
        return brl(amount.subtract(other.amount));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(Objects.requireNonNull(other, "O valor comparado é obrigatório").amount);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof Money other && amount.equals(other.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return CURRENCY + " " + toPlainString();
    }

    private static BigDecimal normalize(BigDecimal amount) {
        Objects.requireNonNull(amount, "O valor monetário é obrigatório");
        final BigDecimal normalized;
        try {
            normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("O valor monetário deve ter no máximo duas casas decimais", exception);
        }
        if (normalized.precision() > PRECISION) {
            throw new IllegalArgumentException("O valor monetário excede o limite de 17 dígitos inteiros");
        }
        return normalized;
    }
}
