package br.com.controlegastos.income.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Registro de auditoria sem regra própria: valida só o que ele guarda. */
class IncomeRevisionTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");

    @Test
    @DisplayName("registra titular, autor, valor, mês e instante da alteração")
    void registraAlteracao() {
        UUID owner = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        IncomeRevision revision = IncomeRevision.record(owner, actor, Money.brl("4321.09"), YearMonth.of(2026, 9), NOW);

        assertThat(revision.ownerId()).isEqualTo(owner);
        assertThat(revision.actorUserId()).isEqualTo(actor);
        assertThat(revision.amount().toPlainString()).isEqualTo("4321.09");
        assertThat(revision.effectiveMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(revision.changedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("cada revisão recebe um identificador próprio")
    void identificadorProprio() {
        UUID owner = UUID.randomUUID();
        IncomeRevision a = IncomeRevision.record(owner, owner, Money.brl("1.00"), YearMonth.of(2026, 9), NOW);
        IncomeRevision b = IncomeRevision.record(owner, owner, Money.brl("1.00"), YearMonth.of(2026, 9), NOW);

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    @DisplayName("renda zero é registrada sem perder a escala")
    void rendaZeroRegistrada() {
        UUID owner = UUID.randomUUID();

        assertThat(IncomeRevision.record(owner, owner, Money.brl("0"), YearMonth.of(2026, 1), NOW)
                .amount().toPlainString()).isEqualTo("0.00");
    }
}
