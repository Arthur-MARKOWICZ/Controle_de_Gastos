package br.com.controlegastos.ledger.events;

import java.time.YearMonth;
import java.util.UUID;

/**
 * O saldo da verba no mês mudou de sinal (não-negativo para negativo ou o inverso).
 * {@code envelopePurpose} é o nome do {@code EnvelopePurpose}; sem valores monetários.
 */
public record EnvelopeBalanceTransitioned(UUID ownerId, UUID envelopeId, String envelopeName,
                                          String envelopePurpose, YearMonth month,
                                          boolean wasNegative, boolean isNegative) {
}
