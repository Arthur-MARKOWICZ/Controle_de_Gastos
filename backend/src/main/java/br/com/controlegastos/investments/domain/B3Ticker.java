package br.com.controlegastos.investments.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código de negociação da B3 (ação, FII, ETF, unit ou BDR), validado só pelo formato.
 * O sufixo {@code F} do mercado fracionário é removido: é o mesmo ativo.
 */
public record B3Ticker(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{4}\\d{1,2}$");

    public B3Ticker {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Ticker inválido");
        }
    }

    public static B3Ticker of(String raw) {
        if (raw == null) throw new IllegalArgumentException("O ticker é obrigatório");
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        if (normalized.endsWith("F") && FORMAT.matcher(normalized.substring(0, normalized.length() - 1)).matches()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return new B3Ticker(normalized);
    }
}
