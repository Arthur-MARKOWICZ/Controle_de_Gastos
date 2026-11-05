package br.com.controlegastos.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.income.application.IncomeQuery;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.ledger.domain.LedgerEntryRevision;
import br.com.controlegastos.ledger.domain.LedgerRevisionAction;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRepository;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRevisionRepository;
import br.com.controlegastos.notifications.application.AlertService;
import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class LedgerEntryRevisionServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ENVELOPE_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

    @Mock EnvelopeService envelopes;
    @Mock LedgerEntryRepository entries;
    @Mock LedgerEntryRevisionRepository revisions;
    @Mock AuthenticationService authentication;
    @Mock IncomeQuery incomes;
    @Mock AlertService alerts;

    private Envelope envelope;
    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        envelope = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("500.00"), NOW);
        lenient().when(authentication.currentUserId()).thenReturn(OWNER);
        lenient().when(envelopes.getVisible(any())).thenReturn(envelope);
        lenient().when(entries.sumAmountBetween(any(), any(String.class), any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(entries.sumAmountUpTo(any(), any(String.class), any())).thenReturn(BigDecimal.ZERO);
        ledger = new LedgerService(
                envelopes, entries, revisions, authentication, Clock.fixed(NOW, ZoneOffset.UTC), incomes, alerts);
    }

    @Test
    void editExpenseCapturesPreviousStateAsRevision() {
        LedgerEntry entry = LedgerEntry.expense(
                ENVELOPE_ID, OWNER, ACTOR, Money.brl("40.00"), LocalDate.of(2026, 11, 3), "feira", NOW);
        when(entries.findById(entry.id())).thenReturn(Optional.of(entry));
        when(entries.saveAndFlush(entry)).thenReturn(entry);
        when(revisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ledger.editExpense(entry.id(), ENVELOPE_ID, Money.brl("55.00"), "feira atualizada");

        ArgumentCaptor<LedgerEntryRevision> captor = ArgumentCaptor.forClass(LedgerEntryRevision.class);
        verify(revisions).save(captor.capture());
        LedgerEntryRevision revision = captor.getValue();
        assertThat(revision.action()).isEqualTo(LedgerRevisionAction.EDIT);
        assertThat(revision.previousAmount()).isEqualTo(Money.brl("40.00"));
        assertThat(revision.previousDescription()).isEqualTo("feira");
        assertThat(revision.previousEnvelopeId()).isEqualTo(ENVELOPE_ID);
        assertThat(revision.actorUserId()).isEqualTo(OWNER);
        assertThat(revision.changedAt()).isEqualTo(NOW);
        assertThat(entry.amount()).isEqualTo(Money.brl("55.00"));
    }

    @Test
    void deleteExpenseCapturesPreviousStateAsRevision() {
        LedgerEntry entry = LedgerEntry.expense(
                ENVELOPE_ID, OWNER, OWNER, Money.brl("12.50"), LocalDate.of(2026, 11, 2), null, NOW);
        when(entries.findById(entry.id())).thenReturn(Optional.of(entry));
        when(entries.saveAndFlush(entry)).thenReturn(entry);
        when(revisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ledger.deleteExpense(entry.id());

        ArgumentCaptor<LedgerEntryRevision> captor = ArgumentCaptor.forClass(LedgerEntryRevision.class);
        verify(revisions).save(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(LedgerRevisionAction.DELETE);
        assertThat(captor.getValue().previousAmount()).isEqualTo(Money.brl("12.50"));
        assertThat(entry.isDeleted()).isTrue();
    }

    @Test
    void idempotentDeleteDoesNotRecordSecondRevision() {
        LedgerEntry entry = LedgerEntry.expense(
                ENVELOPE_ID, OWNER, OWNER, Money.brl("12.50"), LocalDate.of(2026, 11, 2), null, NOW);
        entry.delete(NOW);
        when(entries.findById(entry.id())).thenReturn(Optional.of(entry));

        ledger.deleteExpense(entry.id());

        verify(revisions, never()).save(any());
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void onlyOwnerCanListRevisions() {
        LedgerEntry entry = LedgerEntry.expense(
                ENVELOPE_ID, OWNER, ACTOR, Money.brl("10.00"), LocalDate.of(2026, 11, 1), null, NOW);
        when(entries.findById(entry.id())).thenReturn(Optional.of(entry));
        when(authentication.currentUserId()).thenReturn(ACTOR);

        assertThatThrownBy(() -> ledger.listRevisions(entry.id(), 0, 20))
                .isInstanceOf(EnvelopeForbiddenException.class);
        verify(revisions, never()).findByLedgerEntryIdOrderByChangedAtDescIdDesc(any(), any());
    }

    @Test
    void ownerListsRevisionsNewestFirst() {
        LedgerEntry entry = LedgerEntry.expense(
                ENVELOPE_ID, OWNER, ACTOR, Money.brl("10.00"), LocalDate.of(2026, 11, 1), null, NOW);
        LedgerEntryRevision revision = LedgerEntryRevision.captureBefore(
                entry, LedgerRevisionAction.EDIT, OWNER, NOW);
        when(entries.findById(entry.id())).thenReturn(Optional.of(entry));
        when(revisions.findByLedgerEntryIdOrderByChangedAtDescIdDesc(entry.id(), PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(revision)));

        LedgerService.RevisionPage page = ledger.listRevisions(entry.id(), 0, 20);

        assertThat(page.items()).containsExactly(revision);
        assertThat(page.hasNext()).isFalse();
    }
}
