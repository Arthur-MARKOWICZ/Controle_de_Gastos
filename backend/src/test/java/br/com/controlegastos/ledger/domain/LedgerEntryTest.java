package br.com.controlegastos.ledger.domain;

import org.junit.jupiter.api.DisplayName;
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

    private static final Instant CREATED = Instant.parse("2026-08-10T12:00:00Z");

    private LedgerEntry expense(String amount) {
        return LedgerEntry.expense(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.brl(amount),
                LocalDate.of(2026, 8, 10), null, CREATED);
    }

    @Test
    @DisplayName("aceita gasto de um centavo como menor valor positivo")
    void umCentavoAceito() {
        assertThat(expense("0.01").amount().toPlainString()).isEqualTo("0.01");
    }

    @Test
    @DisplayName("recusa gasto zero com mensagem")
    void gastoZeroFalha() {
        assertThatThrownBy(() -> expense("0.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O valor deve ser positivo");
    }

    @Test
    @DisplayName("recusa gasto negativo com mensagem")
    void gastoNegativoFalha() {
        assertThatThrownBy(() -> expense("-5.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O valor deve ser positivo");
    }

    @Test
    @DisplayName("recusa descrição acima de 140 caracteres")
    void descricaoLongaFalha() {
        assertThatThrownBy(() -> LedgerEntry.expense(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.brl("1.00"), LocalDate.of(2026, 8, 10), "x".repeat(141), CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A descrição deve ter até 140 caracteres");
    }

    @Test
    @DisplayName("descrição em branco vira nula e descrição com espaços é aparada")
    void descricaoNormalizada() {
        LedgerEntry blank = LedgerEntry.expense(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.brl("1.00"), LocalDate.of(2026, 8, 10), "   ", CREATED);
        LedgerEntry padded = LedgerEntry.expense(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.brl("1.00"), LocalDate.of(2026, 8, 10), "  Feira  ", CREATED);

        assertThat(blank.description()).isNull();
        assertThat(padded.description()).isEqualTo("Feira");
    }

    @Test
    @DisplayName("editar gasto excluído logicamente lança IllegalStateException")
    void editarExcluidoFalha() {
        LedgerEntry entry = expense("10.00");
        entry.delete(CREATED.plusSeconds(1));

        assertThatThrownBy(() -> entry.edit(entry.envelopeId(), entry.ownerId(), Money.brl("5.00"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Um gasto excluído não pode ser editado");
        assertThat(entry.amount()).isEqualTo(Money.brl("10.00"));
    }

    @Test
    @DisplayName("excluir duas vezes mantém a primeira data")
    void excluirDuasVezes() {
        LedgerEntry entry = expense("10.00");
        entry.delete(CREATED.plusSeconds(1));
        entry.delete(CREATED.plusSeconds(9));

        assertThat(entry.deletedAt()).isEqualTo(CREATED.plusSeconds(1));
    }

    @Test
    @DisplayName("editar para valor zero lança IllegalArgumentException e preserva o valor")
    void editarParaZeroFalha() {
        LedgerEntry entry = expense("10.00");

        assertThatThrownBy(() -> entry.edit(entry.envelopeId(), entry.ownerId(), Money.zero(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entry.amount()).isEqualTo(Money.brl("10.00"));
    }
}
