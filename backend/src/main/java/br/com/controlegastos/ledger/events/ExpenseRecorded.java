package br.com.controlegastos.ledger.events;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Um gasto foi registrado. Sem valor nem descrição: só autoria, posse, nome e tipo
 * da verba e o mês do gasto.
 */
public record ExpenseRecorded(UUID entryId, UUID envelopeId, String envelopeName, String envelopePurpose,
                              YearMonth month, UUID ownerId, UUID authorId) {
}
