package br.com.controlegastos.envelopes;

import java.util.UUID;

/** Publicado de forma síncrona, na transação do arquivamento (ADR-0031). */
public record EnvelopeArchivedEvent(UUID envelopeId) {
}
