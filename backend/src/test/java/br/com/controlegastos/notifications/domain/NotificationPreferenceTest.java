package br.com.controlegastos.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("recusa horário fora do passo de 15 minutos com a mensagem de validação")
    void recusaHorarioForaDoPassoComMensagem() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> preference.update(true, true, true, LocalTime.of(20, 7), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O horário do lembrete deve usar passos de 15 minutos");
        assertThat(preference.allows(AlertType.NEGATIVE_BALANCE)).isFalse();
        assertThat(preference.allows(AlertType.SHARED_EXPENSE)).isFalse();
    }

    @Test
    @DisplayName("recusa horário ausente com a mensagem de obrigatoriedade")
    void recusaHorarioAusente() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> preference.update(true, false, false, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("O horário do lembrete é obrigatório");
        assertThat(preference.dailyReminder()).isFalse();
    }

    @Test
    @DisplayName("recusa horário com nanossegundos mesmo em minuto múltiplo de 15")
    void recusaHorarioComNanossegundos() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> preference.update(true, false, false, LocalTime.of(20, 15, 0, 1), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O horário do lembrete deve usar passos de 15 minutos");
    }

    @Test
    @DisplayName("lembrete desligado nunca marca o dia como lembrado")
    void lembreteDesligadoNaoMarcaODia() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);
        preference.update(false, false, false, LocalTime.of(8, 0), NOW);

        preference.skipTodayIfPast(LocalDateTime.of(2026, 11, 24, 12, 0));

        assertThat(preference.lastReminderDate()).isNull();
    }

    @Test
    @DisplayName("dia já lembrado não é sobrescrito ao reprocessar o mesmo dia")
    void diaJaLembradoNaoMuda() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);
        preference.update(true, false, false, LocalTime.of(8, 0), NOW);
        preference.skipTodayIfPast(LocalDateTime.of(2026, 11, 24, 12, 0));

        preference.skipTodayIfPast(LocalDateTime.of(2026, 11, 23, 12, 0));

        assertThat(preference.lastReminderDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    @DisplayName("ativar exatamente no horário do lembrete conta o dia como lembrado")
    void ativarNoHorarioExatoContaODia() {
        NotificationPreference preference = NotificationPreference.defaults(UUID.randomUUID(), NOW);
        preference.update(true, false, false, LocalTime.of(20, 0), NOW);

        preference.skipTodayIfPast(LocalDateTime.of(2026, 11, 24, 20, 0));

        assertThat(preference.lastReminderDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    }
}
