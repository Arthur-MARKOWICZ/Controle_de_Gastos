package br.com.controlegastos.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/** Consentimento por tipo de notificação e horário do lembrete diário (ADR-0033). */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(20, 0);
    private static final int REMINDER_STEP_MINUTES = 15;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "daily_reminder", nullable = false)
    private boolean dailyReminder;

    @Column(name = "negative_balance", nullable = false)
    private boolean negativeBalance;

    @Column(name = "shared_expense", nullable = false)
    private boolean sharedExpense;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;

    @Column(name = "last_reminder_date")
    private LocalDate lastReminderDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    private NotificationPreference(UUID userId, Instant now) {
        this.userId = Objects.requireNonNull(userId);
        this.reminderTime = DEFAULT_REMINDER_TIME;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public static NotificationPreference defaults(UUID userId, Instant now) {
        return new NotificationPreference(userId, now);
    }

    public void update(boolean dailyReminder, boolean negativeBalance, boolean sharedExpense,
                       LocalTime reminderTime, Instant now) {
        requireQuarterHour(reminderTime);
        this.dailyReminder = dailyReminder;
        this.negativeBalance = negativeBalance;
        this.sharedExpense = sharedExpense;
        this.reminderTime = reminderTime;
        this.updatedAt = Objects.requireNonNull(now);
    }

    /**
     * Ao ativar ou mudar o horário depois do horário de hoje, o dia atual conta como
     * já lembrado: o primeiro lembrete sai no próximo horário, não na rodada seguinte.
     */
    public void skipTodayIfPast(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        boolean alreadyHandled = lastReminderDate != null && !lastReminderDate.isBefore(today);
        if (dailyReminder && !alreadyHandled && !reminderTime.isAfter(now.toLocalTime())) {
            lastReminderDate = today;
        }
    }

    public boolean allows(AlertType type) {
        return switch (type) {
            case NEGATIVE_BALANCE -> negativeBalance;
            case SHARED_EXPENSE -> sharedExpense;
        };
    }

    public UUID userId() {
        return userId;
    }

    public boolean dailyReminder() {
        return dailyReminder;
    }

    public boolean negativeBalance() {
        return negativeBalance;
    }

    public boolean sharedExpense() {
        return sharedExpense;
    }

    public LocalTime reminderTime() {
        return reminderTime;
    }

    public LocalDate lastReminderDate() {
        return lastReminderDate;
    }

    private static void requireQuarterHour(LocalTime time) {
        Objects.requireNonNull(time, "O horário do lembrete é obrigatório");
        if (time.getMinute() % REMINDER_STEP_MINUTES != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new IllegalArgumentException("O horário do lembrete deve usar passos de 15 minutos");
        }
    }
}
