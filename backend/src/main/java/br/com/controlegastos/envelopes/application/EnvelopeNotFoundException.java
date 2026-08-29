package br.com.controlegastos.envelopes.application;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

@NamedInterface
public class EnvelopeNotFoundException extends RuntimeException {
    private final UUID envelopeId;

    public EnvelopeNotFoundException(UUID envelopeId) {
        super("Verba não encontrada");
        this.envelopeId = envelopeId;
    }

    public UUID envelopeId() {
        return envelopeId;
    }
}
