package br.com.controlegastos.notifications.web;

import br.com.controlegastos.notifications.application.NotificationPreferenceService;
import br.com.controlegastos.notifications.domain.NotificationPreference;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationPreferenceService preferences;

    NotificationController(NotificationPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping("/preferences")
    PreferencesResponse preferences() {
        return PreferencesResponse.from(preferences.current());
    }

    @PutMapping("/preferences")
    PreferencesResponse update(@RequestBody PreferencesRequest request) {
        return PreferencesResponse.from(preferences.update(
                required(request.dailyReminder()), required(request.negativeBalance()),
                required(request.sharedExpense()), time(request.reminderTime())));
    }

    @GetMapping("/today")
    TodayResponse today() {
        NotificationPreferenceService.TodayStatus status = preferences.today();
        return new TodayResponse(status.date().toString(), status.hasExpense());
    }

    private static boolean required(Boolean value) {
        if (value == null) {
            throw new IllegalArgumentException("Preferência obrigatória ausente");
        }
        return value;
    }

    private static LocalTime time(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("O horário do lembrete é obrigatório");
        }
        try {
            return LocalTime.parse(raw, HOUR_MINUTE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Horário inválido; use HH:mm");
        }
    }

    record PreferencesRequest(Boolean dailyReminder, Boolean negativeBalance, Boolean sharedExpense,
                              String reminderTime) {
    }

    record PreferencesResponse(boolean dailyReminder, boolean negativeBalance, boolean sharedExpense,
                               String reminderTime) {
        static PreferencesResponse from(NotificationPreference preference) {
            return new PreferencesResponse(preference.dailyReminder(), preference.negativeBalance(),
                    preference.sharedExpense(), preference.reminderTime().format(HOUR_MINUTE));
        }
    }

    record TodayResponse(String date, boolean hasExpense) {
    }
}
