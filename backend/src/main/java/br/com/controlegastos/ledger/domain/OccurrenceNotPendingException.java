package br.com.controlegastos.ledger.domain;

import java.util.UUID;

/** A ocorrência já foi confirmada ou descartada. */
public class OccurrenceNotPendingException extends RuntimeException {

    public OccurrenceNotPendingException(UUID occurrenceId) {
        super("A ocorrência " + occurrenceId + " já foi resolvida");
    }
}
