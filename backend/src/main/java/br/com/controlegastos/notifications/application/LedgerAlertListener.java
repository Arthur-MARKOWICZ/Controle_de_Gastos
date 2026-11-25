package br.com.controlegastos.notifications.application;

import br.com.controlegastos.ledger.events.EnvelopeBalanceTransitioned;
import br.com.controlegastos.ledger.events.ExpenseRecorded;
import java.time.Clock;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consome eventos do ledger depois do commit do gasto, em transação própria. O
 * Event Publication Registry guarda o evento até este listener concluir (ADR-0033).
 */
@Component
class LedgerAlertListener {

    private final AlertService alerts;
    private final Clock clock;

    LedgerAlertListener(AlertService alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(EnvelopeBalanceTransitioned event) {
        alerts.onBalanceTransition(event.ownerId(), event.envelopeId(), event.envelopeName(),
                event.envelopePurpose(), event.month(), event.wasNegative(), event.isNegative(), clock.instant());
    }

    @ApplicationModuleListener
    void on(ExpenseRecorded event) {
        alerts.onExpenseRecorded(event.ownerId(), event.authorId(), event.envelopeId(), event.envelopeName(),
                event.envelopePurpose(), event.month(), clock.instant());
    }
}
