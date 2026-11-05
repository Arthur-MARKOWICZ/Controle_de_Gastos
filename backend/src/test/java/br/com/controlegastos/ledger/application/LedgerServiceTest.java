package br.com.controlegastos.ledger.application;

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
}
