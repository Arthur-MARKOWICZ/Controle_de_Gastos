package br.com.controlegastos.notifications.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.ledger.application.LedgerActivity;
import br.com.controlegastos.notifications.domain.NotificationPreference;
import br.com.controlegastos.notifications.infrastructure.NotificationPreferenceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preferências de notificação da pessoa autenticada e a consulta "lançou hoje" (ADR-0033). */
@Service
public class NotificationPreferenceService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private final NotificationPreferenceRepository preferences;
    private final AuthenticationService authentication;
    private final LedgerActivity activity;
    private final Clock clock;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences,
                                         AuthenticationService authentication,
                                         LedgerActivity activity, Clock clock) {
        this.preferences = preferences;
        this.authentication = authentication;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPreference current() {
        return find(authentication.currentUserId());
    }

    @Transactional
    public NotificationPreference update(boolean dailyReminder, boolean negativeBalance, boolean sharedExpense,
                                         LocalTime reminderTime) {
        NotificationPreference preference = find(authentication.currentUserId());
        preference.update(dailyReminder, negativeBalance, sharedExpense, reminderTime, clock.instant());
        preference.skipTodayIfPast(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        return preferences.save(preference);
    }

    @Transactional(readOnly = true)
    public TodayStatus today() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return new TodayStatus(today, activity.hasExpenseOn(authentication.currentUserId(), today));
    }

    /** Preferências de uma conta, com os padrões quando ela nunca salvou nada. */
    @Transactional(readOnly = true)
    public NotificationPreference forUser(UUID userId) {
        return find(userId);
    }

    private NotificationPreference find(UUID userId) {
        return preferences.findById(userId)
                .orElseGet(() -> NotificationPreference.defaults(userId, clock.instant()));
    }

    public record TodayStatus(LocalDate date, boolean hasExpense) {
    }
}
