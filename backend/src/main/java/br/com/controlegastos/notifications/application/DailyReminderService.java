package br.com.controlegastos.notifications.application;

import br.com.controlegastos.ledger.application.LedgerActivity;
import br.com.controlegastos.notifications.infrastructure.NotificationPreferenceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Lembrete diário por Web Push (ADR-0033). Roda a cada 15 minutos e lembra quem tem o
 * horário vencido, sem gasto próprio hoje. {@code last_reminder_date} evita o segundo
 * envio no mesmo dia, inclusive após reinício. Premissa: uma réplica do backend.
 */
@Service
public class DailyReminderService {

    private static final Logger log = LoggerFactory.getLogger(DailyReminderService.class);

    private final NotificationPreferenceRepository preferences;
    private final LedgerActivity activity;
    private final PushDispatcher push;
    private final Clock clock;

    public DailyReminderService(NotificationPreferenceRepository preferences, LedgerActivity activity,
                                PushDispatcher push, Clock clock) {
        this.preferences = preferences;
        this.activity = activity;
        this.push = push;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "America/Sao_Paulo")
    public void sendDue() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(NotificationPreferenceService.BUSINESS_ZONE));
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        List<UUID> due = preferences.findDueForReminder(time, today);
        int sent = 0;
        for (UUID userId : due) {
            if (preferences.claimReminder(userId, today) == 0 || activity.hasExpenseOn(userId, today)) {
                continue;
            }
            push.dailyReminder(userId);
            sent++;
        }
        log.info("daily_reminder_run due={} sent={}", due.size(), sent);
    }
}
