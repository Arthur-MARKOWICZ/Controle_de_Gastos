package br.com.controlegastos.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.notifications.domain.InAppAlert;
import br.com.controlegastos.notifications.infrastructure.InAppAlertRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ENVELOPE = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

    @Mock InAppAlertRepository alerts;
    @Mock AuthenticationService authentication;

    private void authenticatedOwner() {
        when(authentication.currentUserId()).thenReturn(OWNER);
    }

    @Test
    void createsActiveAlertWhenBalanceBecomesNegative() {
        when(alerts.findActiveNegative(OWNER, ENVELOPE, MONTH.atDay(1))).thenReturn(Optional.empty());
        when(alerts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().onBalanceTransition(OWNER, ENVELOPE, "Mercado", MONTH, false, true, NOW);

        ArgumentCaptor<InAppAlert> captor = ArgumentCaptor.forClass(InAppAlert.class);
        verify(alerts).save(captor.capture());
        InAppAlert saved = captor.getValue();
        assertThat(saved.ownerId()).isEqualTo(OWNER);
        assertThat(saved.envelopeId()).isEqualTo(ENVELOPE);
        assertThat(saved.envelopeName()).isEqualTo("Mercado");
        assertThat(saved.referenceMonth()).isEqualTo(MONTH.atDay(1));
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void doesNotDuplicateActiveAlertForSameEnvelopeAndMonth() {
        InAppAlert existing = InAppAlert.negativeBalance(OWNER, ENVELOPE, "Mercado", MONTH, NOW);
        when(alerts.findActiveNegative(OWNER, ENVELOPE, MONTH.atDay(1))).thenReturn(Optional.of(existing));

        service().onBalanceTransition(OWNER, ENVELOPE, "Mercado", MONTH, false, true, NOW);

        verify(alerts, never()).save(any());
    }

    @Test
    void resolvesActiveAlertWhenBalanceRecovers() {
        InAppAlert existing = InAppAlert.negativeBalance(OWNER, ENVELOPE, "Mercado", MONTH, NOW);
        when(alerts.findActiveNegative(OWNER, ENVELOPE, MONTH.atDay(1))).thenReturn(Optional.of(existing));

        service().onBalanceTransition(OWNER, ENVELOPE, "Mercado", MONTH, true, false, NOW.plusSeconds(60));

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.resolvedAt()).isEqualTo(NOW.plusSeconds(60));
        verify(alerts).save(existing);
    }

    @Test
    void dismissesOwnActiveAlert() {
        authenticatedOwner();
        InAppAlert existing = InAppAlert.negativeBalance(OWNER, ENVELOPE, "Mercado", MONTH, NOW);
        when(alerts.findById(existing.id())).thenReturn(Optional.of(existing));

        service().dismiss(existing.id(), NOW.plusSeconds(10));

        assertThat(existing.dismissedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(existing.isActive()).isFalse();
        verify(alerts).save(existing);
    }

    @Test
    void refusesToDismissAlertOfAnotherOwner() {
        authenticatedOwner();
        InAppAlert foreign = InAppAlert.negativeBalance(OTHER, ENVELOPE, "Mercado", MONTH, NOW);
        when(alerts.findById(foreign.id())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().dismiss(foreign.id(), NOW))
                .isInstanceOf(AlertNotFoundException.class);
        verify(alerts, never()).save(any());
    }

    @Test
    void listsOnlyActiveAlertsForCurrentUser() {
        authenticatedOwner();
        InAppAlert active = InAppAlert.negativeBalance(OWNER, ENVELOPE, "Mercado", MONTH, NOW);
        when(alerts.findActiveByOwner(OWNER)).thenReturn(List.of(active));

        List<InAppAlert> listed = service().listActive();

        assertThat(listed).containsExactly(active);
    }

    private AlertService service() {
        return new AlertService(alerts, authentication);
    }
}
