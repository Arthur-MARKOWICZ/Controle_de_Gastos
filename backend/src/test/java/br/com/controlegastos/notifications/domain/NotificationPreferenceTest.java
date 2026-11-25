package br.com.controlegastos.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPreferenceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Test
    void startsWithEverythingOffAndReminderAtEightPm() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);

        assertThat(preference.dailyReminder()).isFalse();
        assertThat(preference.allows(AlertType.NEGATIVE_BALANCE)).isFalse();
        assertThat(preference.allows(AlertType.SHARED_EXPENSE)).isFalse();
        assertThat(preference.reminderTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void acceptsQuarterHourSteps() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);

        preference.update(true, true, false, LocalTime.of(21, 45), NOW);

        assertThat(preference.dailyReminder()).isTrue();
        assertThat(preference.allows(AlertType.NEGATIVE_BALANCE)).isTrue();
        assertThat(preference.allows(AlertType.SHARED_EXPENSE)).isFalse();
        assertThat(preference.reminderTime()).isEqualTo(LocalTime.of(21, 45));
    }

    @Test
    void rejectsTimesOutsideTheQuarterHourStepAndKeepsThePrevious() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> preference.update(true, false, false, LocalTime.of(21, 10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> preference.update(true, false, false, LocalTime.of(21, 15, 30), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(preference.dailyReminder()).isFalse();
        assertThat(preference.reminderTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void enablingAfterTodaysTimeCountsTodayAsReminded() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);
        preference.update(true, false, false, LocalTime.of(8, 0), NOW);

        preference.skipTodayIfPast(java.time.LocalDateTime.of(2026, 11, 24, 12, 0));

        assertThat(preference.lastReminderDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 24));
    }

    @Test
    void enablingBeforeTodaysTimeKeepsTodaysReminder() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);
        preference.update(true, false, false, LocalTime.of(20, 0), NOW);

        preference.skipTodayIfPast(java.time.LocalDateTime.of(2026, 11, 24, 12, 0));

        assertThat(preference.lastReminderDate()).isNull();
    }
}
