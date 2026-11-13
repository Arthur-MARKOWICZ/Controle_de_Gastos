package br.com.controlegastos.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecurringOccurrenceTest {

    private static final Instant NOW = Instant.parse("2026-12-10T12:00:00Z");

    private RecurringOccurrence pending() {
        return RecurringOccurrence.pending(UUID.randomUUID(), UUID.randomUUID(), YearMonth.of(2026, 12),
                LocalDate.of(2026, 12, 5), Money.brl("1800.00"), NOW);
    }

    @Test
    void startsPending() {
        RecurringOccurrence occurrence = pending();

        assertThat(occurrence.status()).isEqualTo(OccurrenceStatus.PENDING);
        assertThat(occurrence.competence()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(occurrence.dueDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(occurrence.suggestedAmount()).isEqualTo(Money.brl("1800.00"));
        assertThat(occurrence.ledgerEntryId()).isNull();
    }

    @Test
    void confirmLinksTheLedgerEntry() {
        RecurringOccurrence occurrence = pending();
        UUID entryId = UUID.randomUUID();

        occurrence.confirm(entryId, NOW);

        assertThat(occurrence.status()).isEqualTo(OccurrenceStatus.CONFIRMED);
        assertThat(occurrence.ledgerEntryId()).isEqualTo(entryId);
        assertThat(occurrence.resolvedAt()).isEqualTo(NOW);
    }

    @Test
    void discardResolvesWithoutEntry() {
        RecurringOccurrence occurrence = pending();

        occurrence.discard(NOW);

        assertThat(occurrence.status()).isEqualTo(OccurrenceStatus.DISCARDED);
        assertThat(occurrence.ledgerEntryId()).isNull();
    }

    @Test
    void resolvedOccurrenceCannotBeResolvedAgain() {
        RecurringOccurrence confirmed = pending();
        confirmed.confirm(UUID.randomUUID(), NOW);
        RecurringOccurrence discarded = pending();
        discarded.discard(NOW);

        assertThatThrownBy(() -> confirmed.confirm(UUID.randomUUID(), NOW)).isInstanceOf(OccurrenceNotPendingException.class);
        assertThatThrownBy(() -> confirmed.discard(NOW)).isInstanceOf(OccurrenceNotPendingException.class);
        assertThatThrownBy(() -> discarded.confirm(UUID.randomUUID(), NOW)).isInstanceOf(OccurrenceNotPendingException.class);
        org.assertj.core.api.Assertions.assertThatCode(pending()::requirePending).doesNotThrowAnyException();
    }
}
