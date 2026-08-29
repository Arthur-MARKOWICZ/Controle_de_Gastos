package br.com.controlegastos.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    @Test
    void ownerCanEditExpenseWithoutChangingItsOccurrenceDateOrAuthor() {
        UUID originalEnvelope = UUID.randomUUID();
        UUID destinationEnvelope = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        LocalDate occurredAt = LocalDate.of(2026, 8, 10);
        LedgerEntry entry = LedgerEntry.expense(
                originalEnvelope, owner, author, Money.brl("12.50"), occurredAt,
                "Padaria", Instant.parse("2026-08-10T12:00:00Z"));

        entry.edit(destinationEnvelope, owner, Money.brl("18.75"), "Mercado");

        assertThat(entry.envelopeId()).isEqualTo(destinationEnvelope);
        assertThat(entry.ownerId()).isEqualTo(owner);
        assertThat(entry.amount()).isEqualTo(Money.brl("18.75"));
        assertThat(entry.description()).isEqualTo("Mercado");
        assertThat(entry.occurredAt()).isEqualTo(occurredAt);
        assertThat(entry.authorId()).isEqualTo(author);
    }

    @Test
    void onlyExpensesCanBeEditedOrLogicallyDeleted() {
        LedgerEntry contribution = LedgerEntry.contribution(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.brl("20.00"),
                LocalDate.of(2026, 8, 10), null, Instant.parse("2026-08-10T12:00:00Z"));

        assertThatThrownBy(() -> contribution.edit(
                UUID.randomUUID(), UUID.randomUUID(), Money.brl("21.00"), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> contribution.delete(Instant.parse("2026-08-11T12:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingAnExpenseKeepsTheRecordButMarksItDeleted() {
        LedgerEntry entry = LedgerEntry.expense(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.brl("20.00"),
                LocalDate.of(2026, 8, 10), null, Instant.parse("2026-08-10T12:00:00Z"));
        Instant deletedAt = Instant.parse("2026-08-11T12:00:00Z");

        entry.delete(deletedAt);

        assertThat(entry.isDeleted()).isTrue();
        assertThat(entry.deletedAt()).isEqualTo(deletedAt);
    }
}
