package br.com.controlegastos.notifications.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.notifications.domain.InAppAlert;
import br.com.controlegastos.notifications.infrastructure.InAppAlertRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NamedInterface("alerts")
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final InAppAlertRepository alerts;
    private final AuthenticationService authentication;

    public AlertService(InAppAlertRepository alerts, AuthenticationService authentication) {
        this.alerts = alerts;
        this.authentication = authentication;
    }

    /**
     * Registra transição de saldo negativo sem persistir valores monetários.
     * Chamado pelo módulo ledger após mutações; não usa o usuário da sessão.
     */
    @Transactional
    public void onBalanceTransition(UUID ownerId, UUID envelopeId, String envelopeName, YearMonth month,
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
