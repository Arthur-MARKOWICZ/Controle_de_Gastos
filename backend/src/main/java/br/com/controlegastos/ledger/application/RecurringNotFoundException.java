package br.com.controlegastos.ledger.application;

import java.util.UUID;

public class RecurringNotFoundException extends RuntimeException {

    public RecurringNotFoundException(UUID id) {
        super("Recorrência ou ocorrência não encontrada: " + id);
    }
}
