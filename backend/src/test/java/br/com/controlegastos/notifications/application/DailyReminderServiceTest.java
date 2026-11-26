package br.com.controlegastos.notifications.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
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
    private static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");

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

    @Test
    @DisplayName("sem ninguém com horário vencido não reserva nem envia lembrete")
    void naoEnviaQuandoNinguemEstaNoHorario() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of());

        service().sendDue();

        verify(preferences, never()).claimReminder(any(), any());
        verifyNoInteractions(activity, push);
    }

    @Test
    @DisplayName("lembrete já reservado por outra rodada não impede o lembrete dos demais titulares")
    void reservaDeUmTitularNaoBloqueiaOsOutros() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of(USER, OTHER));
        when(preferences.claimReminder(USER, TODAY)).thenReturn(0);
        when(preferences.claimReminder(OTHER, TODAY)).thenReturn(1);
        when(activity.hasExpenseOn(OTHER, TODAY)).thenReturn(false);

        service().sendDue();

        verify(push, never()).dailyReminder(USER);
        verify(push).dailyReminder(OTHER);
    }

    @Test
    @DisplayName("falha ao buscar quem deve ser lembrado propaga o erro de acesso a dados sem enviar nada")
    void falhaNaBuscaPropagaErroDeDados() {
        when(preferences.findDueForReminder(TIME, TODAY))
                .thenThrow(new DataAccessResourceFailureException("banco indisponível"));

        assertThatThrownBy(() -> service().sendDue())
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("banco indisponível");
        verifyNoInteractions(activity, push);
    }

    @Test
    @DisplayName("falha ao reservar o lembrete propaga o erro e não envia push")
    void falhaNaReservaPropagaErro() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of(USER));
        when(preferences.claimReminder(USER, TODAY))
                .thenThrow(new DataAccessResourceFailureException("conflito de escrita"));

        assertThatThrownBy(() -> service().sendDue())
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("conflito de escrita");
        verifyNoInteractions(push);
    }

    @Test
    @DisplayName("falha ao consultar os gastos do dia propaga o erro e não envia push")
    void falhaNaConsultaDeGastosPropagaErro() {
        when(preferences.findDueForReminder(TIME, TODAY)).thenReturn(List.of(USER));
        when(preferences.claimReminder(USER, TODAY)).thenReturn(1);
        when(activity.hasExpenseOn(USER, TODAY))
                .thenThrow(new DataAccessResourceFailureException("ledger indisponível"));

        assertThatThrownBy(() -> service().sendDue())
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("ledger indisponível");
        verifyNoInteractions(push);
    }

    private DailyReminderService service() {
        return new DailyReminderService(preferences, activity, push, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
