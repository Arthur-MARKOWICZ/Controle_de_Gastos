package br.com.controlegastos.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerEntryRevisionTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private final UUID envelope = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();

    private LedgerEntry expense(String amount, String description) {
        return LedgerEntry.expense(envelope, owner, author, Money.brl(amount), LocalDate.of(2026, 9, 10), description, NOW);
    }

    @Test
    @DisplayName("captura o estado anterior do gasto antes da edição")
    void capturaEstadoAnterior() {
        LedgerEntry entry = expense("40.00", "Feira");

        LedgerEntryRevision revision = LedgerEntryRevision.captureBefore(entry, LedgerRevisionAction.EDIT, author, NOW);

        assertThat(revision.ledgerEntryId()).isEqualTo(entry.id());
        assertThat(revision.ownerId()).isEqualTo(owner);
        assertThat(revision.previousEnvelopeId()).isEqualTo(envelope);
        assertThat(revision.previousAmount().toPlainString()).isEqualTo("40.00");
        assertThat(revision.previousDescription()).isEqualTo("Feira");
        assertThat(revision.action()).isEqualTo(LedgerRevisionAction.EDIT);
    }

    @Test
    @DisplayName("a revisão não muda quando o gasto é editado depois")
    void revisaoImutavelAposEdicao() {
        LedgerEntry entry = expense("40.00", "Feira");
        LedgerEntryRevision revision = LedgerEntryRevision.captureBefore(entry, LedgerRevisionAction.EDIT, author, NOW);

        entry.edit(envelope, owner, Money.brl("35.00"), "Feira ajustada");

        assertThat(revision.previousAmount()).isEqualTo(Money.brl("40.00"));
        assertThat(revision.previousDescription()).isEqualTo("Feira");
    }

    @Test
    @DisplayName("registra quem fez a ação, mesmo quando é o proprietário editando gasto de participante")
    void registraAutorDaAcao() {
        LedgerEntryRevision revision =
                LedgerEntryRevision.captureBefore(expense("1.00", null), LedgerRevisionAction.DELETE, owner, NOW);

        assertThat(revision.actorUserId()).isEqualTo(owner);
        assertThat(revision.action()).isEqualTo(LedgerRevisionAction.DELETE);
    }

    @Test
    @DisplayName("descrição ausente é aceita como nula")
    void descricaoAusenteAceita() {
        assertThat(LedgerEntryRevision.captureBefore(expense("0.01", null), LedgerRevisionAction.EDIT, author, NOW)
                .previousDescription()).isNull();
    }

    @Test
    @DisplayName("revisão sem autor lança NullPointerException")
    void semAutorFalha() {
        assertThatThrownBy(() -> LedgerEntryRevision.captureBefore(expense("1.00", null), LedgerRevisionAction.EDIT, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("revisão sem ação lança NullPointerException")
    void semAcaoFalha() {
        assertThatThrownBy(() -> LedgerEntryRevision.captureBefore(expense("1.00", null), null, author, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("revisão sem instante lança NullPointerException")
    void semInstanteFalha() {
        assertThatThrownBy(() -> LedgerEntryRevision.captureBefore(expense("1.00", null), LedgerRevisionAction.EDIT, author, null))
                .isInstanceOf(NullPointerException.class);
    }
}
