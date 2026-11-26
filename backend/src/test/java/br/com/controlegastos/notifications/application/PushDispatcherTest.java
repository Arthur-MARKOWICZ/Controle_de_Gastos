package br.com.controlegastos.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.controlegastos.notifications.domain.AlertType;
import br.com.controlegastos.notifications.domain.NotificationPreference;
import br.com.controlegastos.notifications.domain.WebPushSubscription;
import br.com.controlegastos.notifications.infrastructure.NotificationPreferenceRepository;
import br.com.controlegastos.notifications.infrastructure.WebPushSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDispatcherTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-24T23:00:00Z");

    @Mock WebPushSender sender;
    @Mock WebPushSubscriptionRepository subscriptions;
    @Mock NotificationPreferenceRepository preferences;

    private WebPushSubscription first;
    private WebPushSubscription second;

    @BeforeEach
    void setUp() {
        lenient().when(sender.isEnabled()).thenReturn(true);
        first = subscription("https://push.example.com/a");
        second = subscription("https://push.example.com/b");
    }

    @Test
    void sendsNothingWhenTheTypeIsOff() {
        when(preferences.findById(OWNER)).thenReturn(Optional.of(NotificationPreference.defaults(OWNER, NOW)));

        dispatcher().alert(OWNER, AlertType.NEGATIVE_BALANCE, "LIMIT");

        verify(sender, never()).send(any(), anyString());
    }

    @Test
    void sendsToEverySubscriptionWithMinimalPayload() {
        enable(AlertType.NEGATIVE_BALANCE);
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first, second));
        when(sender.send(any(), anyString())).thenReturn(WebPushSender.Outcome.DELIVERED);

        dispatcher().alert(OWNER, AlertType.NEGATIVE_BALANCE, "LIMIT");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.times(2)).send(any(), payload.capture());
        assertThat(payload.getValue())
                .isEqualTo("{\"category\":\"NEGATIVE_BALANCE\",\"purpose\":\"LIMIT\",\"path\":\"/\"}");
    }

    @Test
    void deletesGoneSubscriptionsAndKeepsGoingAfterAFailure() {
        enable(AlertType.SHARED_EXPENSE);
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first, second));
        when(sender.send(any(), anyString()))
                .thenReturn(WebPushSender.Outcome.GONE)
                .thenReturn(WebPushSender.Outcome.DELIVERED);

        dispatcher().alert(OWNER, AlertType.SHARED_EXPENSE, "GOAL");

        verify(subscriptions).delete(first);
        verify(subscriptions, never()).delete(second);
    }

    @Test
    void reportsTransientFailureAfterTryingEverySubscription() {
        enable(AlertType.SHARED_EXPENSE);
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first, second));
        when(sender.send(any(), anyString()))
                .thenReturn(WebPushSender.Outcome.FAILED)
                .thenReturn(WebPushSender.Outcome.DELIVERED);

        assertThatThrownBy(() -> dispatcher().alert(OWNER, AlertType.SHARED_EXPENSE, "GOAL"))
                .isInstanceOf(PushDeliveryException.class);

        verify(sender, org.mockito.Mockito.times(2)).send(any(), anyString());
        verify(subscriptions, never()).delete(any());
    }

    @Test
    void dailyReminderPayloadHasNoEnvelopeType() {
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first));
        when(sender.send(any(), anyString())).thenReturn(WebPushSender.Outcome.DELIVERED);

        dispatcher().dailyReminder(OWNER);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sender).send(any(), payload.capture());
        assertThat(payload.getValue()).isEqualTo("{\"category\":\"DAILY_REMINDER\",\"path\":\"/\"}");
    }

    @Test
    void doesNothingWhenPushIsDisabled() {
        when(sender.isEnabled()).thenReturn(false);

        dispatcher().alert(OWNER, AlertType.NEGATIVE_BALANCE, "LIMIT");

        verify(sender, never()).send(any(), anyString());
    }

    @Test
    @DisplayName("titular sem preferência gravada não recebe alerta")
    void naoEnviaSemPreferenciaGravada() {
        when(preferences.findById(OWNER)).thenReturn(Optional.empty());

        dispatcher().alert(OWNER, AlertType.NEGATIVE_BALANCE, "LIMIT");

        verifyNoInteractions(subscriptions);
        verify(sender, never()).send(any(), anyString());
    }

    @Test
    @DisplayName("lembrete diário com push desligado não consulta assinaturas nem envia")
    void lembreteNaoEnviaComPushDesligado() {
        when(sender.isEnabled()).thenReturn(false);

        dispatcher().dailyReminder(OWNER);

        verifyNoInteractions(subscriptions);
        verify(sender, never()).send(any(), anyString());
    }

    @Test
    @DisplayName("falha temporária no lembrete diário não lança erro e mantém a assinatura")
    void falhaNoLembreteNaoLancaNemApagaAssinatura() {
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first));
        when(sender.send(any(), anyString())).thenReturn(WebPushSender.Outcome.FAILED);

        dispatcher().dailyReminder(OWNER);

        verify(subscriptions, never()).delete(any());
    }

    @Test
    @DisplayName("alerta informa quantos envios falharam na mensagem da exceção")
    void alertaInformaQuantidadeDeFalhas() {
        enable(AlertType.NEGATIVE_BALANCE);
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first, second));
        when(sender.send(any(), anyString())).thenReturn(WebPushSender.Outcome.FAILED);

        assertThatThrownBy(() -> dispatcher().alert(OWNER, AlertType.NEGATIVE_BALANCE, "LIMIT"))
                .isInstanceOf(PushDeliveryException.class)
                .hasMessage("Falha temporária em 2 envio(s) de push");
    }

    @Test
    @DisplayName("erro inesperado do remetente interrompe o alerta sem apagar assinaturas")
    void erroInesperadoDoRemetentePropaga() {
        enable(AlertType.NEGATIVE_BALANCE);
        when(subscriptions.findByUserId(OWNER)).thenReturn(List.of(first));
        when(sender.send(any(), anyString())).thenThrow(new IllegalStateException("cliente HTTP fechado"));

        assertThatThrownBy(() -> dispatcher().alert(OWNER, AlertType.NEGATIVE_BALANCE, "LIMIT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cliente HTTP fechado");
        verify(subscriptions, never()).delete(any());
    }

    private void enable(AlertType type) {
        NotificationPreference preference = NotificationPreference.defaults(OWNER, NOW);
        preference.update(false, type == AlertType.NEGATIVE_BALANCE, type == AlertType.SHARED_EXPENSE,
                LocalTime.of(20, 0), NOW);
        when(preferences.findById(OWNER)).thenReturn(Optional.of(preference));
    }

    private PushDispatcher dispatcher() {
        return new PushDispatcher(sender, subscriptions, preferences, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static WebPushSubscription subscription(String endpoint) {
        byte[] key = new byte[65];
        key[0] = 4;
        return WebPushSubscription.create(OWNER, endpoint, Base64.getUrlEncoder().withoutPadding().encodeToString(key),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]), NOW);
    }
}
