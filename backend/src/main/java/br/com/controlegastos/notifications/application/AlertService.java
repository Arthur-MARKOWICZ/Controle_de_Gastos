package br.com.controlegastos.notifications.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.notifications.domain.AlertType;
import br.com.controlegastos.notifications.domain.InAppAlert;
import br.com.controlegastos.notifications.infrastructure.InAppAlertRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface("alerts")
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final InAppAlertRepository alerts;
    private final AuthenticationService authentication;
    private final ApplicationEventPublisher events;

    public AlertService(InAppAlertRepository alerts, AuthenticationService authentication,
                        ApplicationEventPublisher events) {
        this.alerts = alerts;
        this.authentication = authentication;
        this.events = events;
    }

    /**
     * Registra transição de saldo negativo sem persistir valores monetários.
     * Chamado pelo módulo ledger após mutações; não usa o usuário da sessão.
     */
    @Transactional
    public void onBalanceTransition(UUID ownerId, UUID envelopeId, String envelopeName, String envelopePurpose,
                                    YearMonth month,
                                    boolean wasNegative, boolean isNegative, Instant now) {
        if (wasNegative == isNegative) {
            return;
        }
        if (!wasNegative && isNegative) {
            if (alerts.findActiveNegative(ownerId, envelopeId, month.atDay(1)).isPresent()) {
                return;
            }
            InAppAlert alert = InAppAlert.negativeBalance(ownerId, envelopeId, envelopeName, month, now);
            alerts.save(alert);
            events.publishEvent(new AlertRaised(ownerId, AlertType.NEGATIVE_BALANCE, envelopePurpose));
            log.info("negative_balance_alert_created alertId={} envelopeId={} ownerId={}",
                    alert.id(), envelopeId, ownerId);
            return;
        }
        alerts.findActiveNegative(ownerId, envelopeId, month.atDay(1)).ifPresent(alert -> {
            alert.resolve(now);
            alerts.save(alert);
            log.info("negative_balance_alert_resolved alertId={} envelopeId={} ownerId={}",
                    alert.id(), envelopeId, ownerId);
        });
    }

    /**
     * Avisa o dono quando um participante registra gasto em verba compartilhada.
     * Não guarda valor, descrição nem quem lançou (ADR-0026, ADR-0033).
     */
    @Transactional
    public void onExpenseRecorded(UUID ownerId, UUID authorId, UUID envelopeId, String envelopeName,
                                  String envelopePurpose, YearMonth month, Instant now) {
        if (ownerId.equals(authorId)) {
            return;
        }
        InAppAlert alert = alerts.save(InAppAlert.sharedExpense(ownerId, envelopeId, envelopeName, month, now));
        events.publishEvent(new AlertRaised(ownerId, AlertType.SHARED_EXPENSE, envelopePurpose));
        log.info("shared_expense_alert_created alertId={} envelopeId={} ownerId={}", alert.id(), envelopeId, ownerId);
    }

    @Transactional(readOnly = true)
    public List<InAppAlert> listActive() {
        return alerts.findActiveByOwner(authentication.currentUserId());
    }

    @Transactional
    public void dismiss(UUID alertId, Instant now) {
        UUID userId = authentication.currentUserId();
        InAppAlert alert = alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
        if (!alert.ownerId().equals(userId)) {
            throw new AlertNotFoundException(alertId);
        }
        alert.dismiss(now);
        alerts.save(alert);
        log.info("negative_balance_alert_dismissed alertId={} ownerId={}", alert.id(), userId);
    }
}
