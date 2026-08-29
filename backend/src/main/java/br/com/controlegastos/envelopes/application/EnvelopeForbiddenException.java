package br.com.controlegastos.envelopes.application;

import org.springframework.modulith.NamedInterface;

@NamedInterface
public class EnvelopeForbiddenException extends RuntimeException {
    public EnvelopeForbiddenException(String message) {
        super(message);
    }
}
