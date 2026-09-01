package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvelopeTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void createsSavingsTargetWithAZeroMonthlyAllocationAndTotalTarget() {
        Envelope envelope = Envelope.create(
                UUID.randomUUID(), "Notebook", EnvelopePurpose.SAVINGS_TARGET,
                Money.zero(), Money.brl("1000.00"), NOW);

        assertThat(envelope.baseAmount()).isEqualTo(Money.zero());
        assertThat(envelope.targetAmount()).isEqualTo(Money.brl("1000.00"));
        assertThat(envelope.targetReachedAt()).isNull();
    }

    @Test
    void rejectsSavingsTargetWithMonthlyAllocationOrMissingTarget() {
        assertThatThrownBy(() -> Envelope.create(
                UUID.randomUUID(), "Notebook", EnvelopePurpose.SAVINGS_TARGET,
                Money.brl("10.00"), Money.brl("1000.00"), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Envelope.create(
                UUID.randomUUID(), "Notebook", EnvelopePurpose.SAVINGS_TARGET,
                Money.zero(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsTheFirstTargetAchievementOnlyOnce() {
        Envelope envelope = Envelope.create(
                UUID.randomUUID(), "Notebook", EnvelopePurpose.SAVINGS_TARGET,
                Money.zero(), Money.brl("1000.00"), NOW);
        Instant firstCrossing = Instant.parse("2026-09-10T12:00:00Z");

        assertThat(envelope.recordTargetReached(firstCrossing)).isTrue();
        assertThat(envelope.recordTargetReached(Instant.parse("2026-09-11T12:00:00Z"))).isFalse();
        assertThat(envelope.targetReachedAt()).isEqualTo(firstCrossing);
    }

    @Test
    void changingTheTargetDoesNotClearTheFirstAchievement() {
        Envelope envelope = Envelope.create(
                UUID.randomUUID(), "Notebook", EnvelopePurpose.SAVINGS_TARGET,
                Money.zero(), Money.brl("1000.00"), NOW);
        Instant firstCrossing = Instant.parse("2026-09-10T12:00:00Z");
        envelope.recordTargetReached(firstCrossing);

        envelope.changeTargetAmount(Money.brl("2000.00"));

        assertThat(envelope.targetAmount()).isEqualTo(Money.brl("2000.00"));
        assertThat(envelope.targetReachedAt()).isEqualTo(firstCrossing);
    }
}
