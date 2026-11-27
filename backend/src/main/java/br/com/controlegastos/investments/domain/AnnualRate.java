package br.com.controlegastos.investments.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Taxa anual prefixada em decimal com exatamente quatro casas (ADR-0034).
 * {@code 0.1250} equivale a 12,50% a.a.; não há conversão percentual.
 */
public record AnnualRate(BigDecimal value) {

    private static final int SCALE = 4;

    public AnnualRate {
        Objects.requireNonNull(value, "A taxa anual é obrigatória");
        if (value.scale() != SCALE || value.signum() <= 0) {
            throw new IllegalArgumentException("A taxa anual deve ser positiva com exatamente quatro casas decimais");
        }
    }

    public static AnnualRate of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("A taxa anual é obrigatória");
        }
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("A taxa anual deve ser um decimal válido", exception);
        }
        if (parsed.scale() != SCALE || parsed.signum() <= 0 || !parsed.toPlainString().equals(raw.strip())) {
            throw new IllegalArgumentException("A taxa anual deve ser positiva com exatamente quatro casas decimais");
        }
        return new AnnualRate(parsed);
    }

    public String toPlainString() {
        return value.toPlainString();
    }
}
