package br.com.controlegastos.notifications.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.ledger.application.LedgerActivity;
import br.com.controlegastos.notifications.infrastructure.NotificationPreferenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyReminderServiceTest {

    /** 23:00 UTC = 20:00 em São Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-24T23:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final LocalTime TIME = LocalTime.of(20, 0);
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock NotificationPreferenceRepository preferences;
    @Mock LedgerActivity activity;
    @Mock PushDispatcher push;

    @Test
    void remindsWhoHasNoExpenseTodayUsingSaoPauloTime() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of(USER));
        when(preferences.claimReminder(USER, TODAY)).thenReturn(1);
        when(activity.hasExpenseOn(USER, TODAY)).thenReturn(false);

        service().sendDue();

        verify(push).dailyReminder(USER);
    }

    @Test
    void skipsWhoAlreadyRecordedAnExpense() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of(USER));
        when(preferences.claimReminder(USER, TODAY)).thenReturn(1);
        when(activity.hasExpenseOn(USER, TODAY)).thenReturn(true);

        service().sendDue();

        verify(push, never()).dailyReminder(USER);
    }

    @Test
    void neverSendsTwiceOnTheSameDay() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of(USER));
        when(preferences.claimReminder(USER, TODAY)).thenReturn(0);

        service().sendDue();

        verify(push, never()).dailyReminder(USER);
        verify(activity, never()).hasExpenseOn(USER, TODAY);
    }

    private DailyReminderService service() {
        return new DailyReminderService(preferences, activity, push, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
