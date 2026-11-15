package br.com.controlegastos.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringEntryTest {

    private static final Instant NOW = Instant.parse("2026-12-10T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 10);
    private final UUID envelopeId = UUID.randomUUID();

    private RecurringEntry rent(int day) {
        return RecurringEntry.create(envelopeId, LedgerKind.EXPENSE, Money.brl("1800.00"), day, "  Aluguel  ", TODAY, NOW);
    }

    @Test
    @DisplayName("criação ativa, com descrição normalizada e primeira competência calculada")
    void createsActiveRecurrence() {
        RecurringEntry entry = rent(15);

        assertThat(entry.active()).isTrue();
        assertThat(entry.description()).isEqualTo("Aluguel");
        assertThat(entry.amount()).isEqualTo(Money.brl("1800.00"));
        assertThat(entry.firstCompetence()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(rent(5).firstCompetence()).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> RecurringEntry.create(envelopeId, LedgerKind.EXPENSE, Money.zero(), 5, null, TODAY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecurringEntry.create(envelopeId, LedgerKind.EXPENSE, Money.brl("-1.00"), 5, null, TODAY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidDayAndLongDescription() {
        assertThatThrownBy(() -> rent(32)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecurringEntry.create(envelopeId, LedgerKind.EXPENSE, Money.brl("1.00"), 5, "x".repeat(141), TODAY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("blank description becomes null")
    void blankDescriptionBecomesNull() {
        assertThat(RecurringEntry.create(envelopeId, LedgerKind.EXPENSE, Money.brl("1.00"), 5, "   ", TODAY, NOW).description()).isNull();
    }

    @Test
    @DisplayName("reativar depois do dia previsto começa no mês seguinte")
    void reactivationAfterDueDayStartsNextMonth() {
        RecurringEntry entry = rent(15);
        entry.deactivate();
        assertThat(entry.active()).isFalse();

        entry.activate(LocalDate.of(2026, 12, 20));

        assertThat(entry.active()).isTrue();
        assertThat(entry.firstCompetence()).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    @DisplayName("editar o dia nunca antecipa a primeira competência")
    void updateNeverMovesFirstCompetenceBackwards() {
        RecurringEntry entry = rent(5); // começa em 2027-01

        entry.update(LedgerKind.EXPENSE, Money.brl("1900.00"), 20, "Aluguel novo", TODAY);

        assertThat(entry.amount()).isEqualTo(Money.brl("1900.00"));
        assertThat(entry.dayOfMonth()).isEqualTo(20);
        assertThat(entry.description()).isEqualTo("Aluguel novo");
        assertThat(entry.firstCompetence()).isEqualTo(YearMonth.of(2027, 1));
    }

    @Test
    @DisplayName("exclusão lógica desativa e não pode ser editada nem reativada")
    void deletionIsLogicalAndFinal() {
        RecurringEntry entry = rent(15);

        entry.delete(NOW);

        assertThat(entry.isDeleted()).isTrue();
        assertThat(entry.active()).isFalse();
        assertThatThrownBy(() -> entry.activate(TODAY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> entry.update(LedgerKind.EXPENSE, Money.brl("1.00"), 5, null, TODAY))
                .isInstanceOf(IllegalStateException.class);
    }
}
