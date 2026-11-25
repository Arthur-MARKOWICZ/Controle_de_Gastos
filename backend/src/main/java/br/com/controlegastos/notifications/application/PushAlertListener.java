package br.com.controlegastos.notifications.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Transforma alertas recém-criados em push, fora da transação que criou o alerta. */
@Component
class PushAlertListener {

    private final PushDispatcher push;

    PushAlertListener(PushDispatcher push) {
        this.push = push;
    }

    @ApplicationModuleListener
    void on(AlertRaised event) {
        push.alert(event.ownerId(), event.type(), event.envelopePurpose());
    }
}
