package br.com.controlegastos.identity.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public EmailAddress {
        Objects.requireNonNull(value, "E-mail é obrigatório");
        if (value.length() > 254 || !SIMPLE_EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException("E-mail inválido");
        }
    }

    public static EmailAddress from(String rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException("E-mail é obrigatório");
        }
        return new EmailAddress(rawValue.strip().toLowerCase(Locale.ROOT));
    }
}
