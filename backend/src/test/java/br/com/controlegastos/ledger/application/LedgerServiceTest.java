package br.com.controlegastos.ledger.application;

import java.util.Optional;
import java.time.LocalDate;
import java.math.BigDecimal;
import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.application.IncomeQuery;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRepository;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRevisionRepository;
import br.com.controlegastos.notifications.application.AlertService;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-15T15:00:00Z");

    @Mock EnvelopeService envelopes;
    @Mock LedgerEntryRepository entries;
    @Mock LedgerEntryRevisionRepository revisions;
    @Mock AuthenticationService authentication;
    @Mock IncomeQuery incomes;
    @Mock AlertService alerts;

    @Test
    void returnsZeroAvailableAndGoalProgressWhenTheQueryMonthIsBeforeEnvelopeCreation() {
        Clock clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        LedgerService ledger = new LedgerService(envelopes, entries, revisions, authentication, clock, incomes, alerts);
        Envelope envelope = Envelope.create(
                UUID.randomUUID(), "Investimentos", EnvelopePurpose.GOAL, Money.brl("100.00"), clock.instant());
        YearMonth beforeCreation = YearMonth.of(2026, 8);

        Money available = ledger.availableFor(envelope, beforeCreation);
        LedgerService.GoalProgress progress = ledger.goalProgressFor(envelope, beforeCreation);

        assertThat(available).isEqualTo(Money.zero());
        assertThat(progress.plannedAmount()).isEqualTo(Money.zero());
        assertThat(progress.contributedAmount()).isEqualTo(Money.zero());
        assertThat(progress.remainingAmount()).isEqualTo(Money.zero());
        assertThat(progress.percent()).isZero();
        verifyNoInteractions(entries);
    }

    private final UUID owner = UUID.randomUUID();
    private final UUID participant = UUID.randomUUID();

    private LedgerService ledger() {
        return new LedgerService(envelopes, entries, revisions, authentication,
                Clock.fixed(CREATED_AT, ZoneOffset.UTC), incomes, alerts);
    }

    private Envelope envelope(String base) {
        Envelope envelope = Envelope.create(owner, "Mercado", EnvelopePurpose.LIMIT, Money.brl(base), CREATED_AT.minusSeconds(86400));
        when(envelopes.getVisible(envelope.id())).thenReturn(envelope);
        return envelope;
    }

    private LedgerEntry savedExpense(Envelope envelope, UUID author, String amount) {
        LedgerEntry entry = LedgerEntry.expense(envelope.id(), owner, author, Money.brl(amount),
                LocalDate.of(2026, 9, 10), null, CREATED_AT);
        when(entries.findById(entry.id())).thenReturn(Optional.of(entry));
        return entry;
    }

    @Test
    @DisplayName("participante registra gasto em nome do proprietário da verba")
    void participanteRegistraGasto() {
        Envelope envelope = envelope("100.00");
        when(authentication.currentUserId()).thenReturn(participant);
        when(entries.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerService.Registration registration = ledger().register(envelope.id(), LedgerKind.EXPENSE,
                Money.brl("30.00"), LocalDate.of(2026, 9, 15), "Feira");

        assertThat(registration.entry().ownerId()).isEqualTo(owner);
        assertThat(registration.entry().authorId()).isEqualTo(participant);
        assertThat(registration.entry().amount().toPlainString()).isEqualTo("30.00");
    }

    @Test
    @DisplayName("participante não faz aporte: EnvelopeForbiddenException")
    void participanteNaoFazAporte() {
        Envelope envelope = envelope("100.00");
        when(authentication.currentUserId()).thenReturn(participant);

        assertThatThrownBy(() -> ledger().register(envelope.id(), LedgerKind.CONTRIBUTION,
                Money.brl("10.00"), LocalDate.of(2026, 9, 15), null))
                .isInstanceOf(EnvelopeForbiddenException.class)
                .hasMessage("Somente o proprietário pode fazer aportes");
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("data dois dias à frente do relógio do sistema de negócio é recusada")
    void dataNoFuturoFalha() {
        Envelope envelope = envelope("100.00");
        when(authentication.currentUserId()).thenReturn(owner);

        assertThatThrownBy(() -> ledger().register(envelope.id(), LedgerKind.EXPENSE,
                Money.brl("10.00"), LocalDate.of(2026, 9, 17), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data não pode ser no futuro");
    }

    @Test
    @DisplayName("valor ausente lança IllegalArgumentException com mensagem")
    void valorAusenteFalha() {
        Envelope envelope = envelope("100.00");
        when(authentication.currentUserId()).thenReturn(owner);

        assertThatThrownBy(() -> ledger().register(envelope.id(), LedgerKind.EXPENSE, null, LocalDate.of(2026, 9, 15), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O valor é obrigatório");
    }

    @Test
    @DisplayName("data ausente lança IllegalArgumentException com mensagem")
    void dataAusenteFalha() {
        Envelope envelope = envelope("100.00");
        when(authentication.currentUserId()).thenReturn(owner);

        assertThatThrownBy(() -> ledger().register(envelope.id(), LedgerKind.EXPENSE, Money.brl("1.00"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data é obrigatória");
    }

    @Test
    @DisplayName("gasto que deixa o saldo negativo avisa o serviço de alertas da transição")
    void saldoNegativoDisparaAlerta() {
        Envelope envelope = envelope("100.00");
        when(authentication.currentUserId()).thenReturn(owner);
        when(entries.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(entries.sumAmountBetween(any(UUID.class), org.mockito.ArgumentMatchers.eq(LedgerKind.EXPENSE),
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(null, new BigDecimal("100.01"));

        ledger().register(envelope.id(), LedgerKind.EXPENSE, Money.brl("100.01"), LocalDate.of(2026, 9, 15), null);

        verify(alerts).onBalanceTransition(owner, envelope.id(), "Mercado", YearMonth.of(2026, 9), false, true, CREATED_AT);
    }

    @Test
    @DisplayName("terceiro que não é autor nem proprietário não edita o gasto")
    void terceiroNaoEdita() {
        Envelope envelope = Envelope.create(owner, "Mercado", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED_AT);
        LedgerEntry entry = savedExpense(envelope, participant, "10.00");
        when(authentication.currentUserId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> ledger().editExpense(entry.id(), envelope.id(), Money.brl("5.00"), null))
                .isInstanceOf(EnvelopeForbiddenException.class)
                .hasMessage("Somente o autor ou o proprietário da verba pode editar o gasto");
        verify(revisions, never()).save(any());
    }

    @Test
    @DisplayName("mover gasto para verba de outro proprietário lança EnvelopeForbiddenException")
    void moverParaOutroProprietarioFalha() {
        Envelope source = Envelope.create(owner, "Mercado", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED_AT);
        LedgerEntry entry = savedExpense(source, participant, "10.00");
        Envelope foreign = Envelope.create(participant, "Pessoal", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED_AT);
        when(envelopes.getVisible(foreign.id())).thenReturn(foreign);
        when(authentication.currentUserId()).thenReturn(participant);

        assertThatThrownBy(() -> ledger().editExpense(entry.id(), foreign.id(), Money.brl("5.00"), null))
                .isInstanceOf(EnvelopeForbiddenException.class)
                .hasMessage("Não é permitido mover o gasto para a verba de outro proprietário");
    }

    @Test
    @DisplayName("excluir gasto já excluído não grava nova revisão")
    void excluirDuasVezesSemRevisao() {
        Envelope envelope = Envelope.create(owner, "Mercado", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED_AT);
        LedgerEntry entry = savedExpense(envelope, owner, "10.00");
        entry.delete(CREATED_AT);
        when(authentication.currentUserId()).thenReturn(owner);

        ledger().deleteExpense(entry.id());

        verify(revisions, never()).save(any());
    }

    @Test
    @DisplayName("lançamento inexistente lança LedgerEntryNotFoundException")
    void lancamentoInexistenteFalha() {
        UUID ghost = UUID.randomUUID();
        when(entries.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledger().deleteExpense(ghost)).isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    @DisplayName("participante não consulta o histórico de alterações")
    void participanteNaoConsultaRevisoes() {
        Envelope envelope = Envelope.create(owner, "Mercado", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED_AT);
        LedgerEntry entry = savedExpense(envelope, participant, "10.00");
        when(authentication.currentUserId()).thenReturn(participant);

        assertThatThrownBy(() -> ledger().listRevisions(entry.id(), 0, 20))
                .isInstanceOf(EnvelopeForbiddenException.class)
                .hasMessage("Somente o proprietário pode consultar o histórico de alterações");
    }

    @Test
    @DisplayName("histórico com paginação acima de 100 lança IllegalArgumentException")
    void paginacaoInvalidaFalha() {
        assertThatThrownBy(() -> ledger().history(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), false, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Paginação inválida");
    }

    @Test
    @DisplayName("progresso de verba que não é meta lança IllegalArgumentException")
    void progressoDeNaoMetaFalha() {
        Envelope limit = Envelope.create(owner, "Mercado", EnvelopePurpose.LIMIT, Money.brl("1.00"), CREATED_AT);

        assertThatThrownBy(() -> ledger().goalProgressFor(limit, YearMonth.of(2026, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O progresso acumulado é exclusivo de metas de aporte");
    }
}
