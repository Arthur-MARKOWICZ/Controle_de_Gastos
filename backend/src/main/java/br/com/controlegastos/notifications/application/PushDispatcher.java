package br.com.controlegastos.notifications.application;

import br.com.controlegastos.notifications.domain.AlertType;
import br.com.controlegastos.notifications.domain.WebPushSubscription;
import br.com.controlegastos.notifications.infrastructure.NotificationPreferenceRepository;
import br.com.controlegastos.notifications.infrastructure.WebPushSubscriptionRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Monta o payload mínimo (categoria, tipo da verba e caminho) e o entrega a todas as
 * assinaturas da conta. Nunca inclui nome de verba, valor ou pessoas (ADR-0033).
 */
@Service
public class PushDispatcher {

    static final String DAILY_REMINDER = "DAILY_REMINDER";
    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final WebPushSender sender;
    private final WebPushSubscriptionRepository subscriptions;
    private final NotificationPreferenceRepository preferences;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public PushDispatcher(WebPushSender sender, WebPushSubscriptionRepository subscriptions,
                          NotificationPreferenceRepository preferences, Clock clock) {
        this.sender = sender;
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.clock = clock;
    }

    /** Envia o alerta se o tipo estiver ativo; lança {@link PushDeliveryException} em falha temporária. */
    @Transactional
    public void alert(UUID ownerId, AlertType type, String envelopePurpose) {
        if (!sender.isEnabled()) {
            return;
        }
        boolean allowed = preferences.findById(ownerId).map(preference -> preference.allows(type)).orElse(false);
        if (!allowed) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", type.name());
        payload.put("purpose", envelopePurpose);
        payload.put("path", "/");
        int failures = deliver(ownerId, payload);
        if (failures > 0) {
            throw new PushDeliveryException(failures);
        }
    }

    /** Lembrete diário; falhas ficam só no log, o lembrete não é retentado além do dia. */
    @Transactional
    public void dailyReminder(UUID userId) {
        if (!sender.isEnabled()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", DAILY_REMINDER);
        // Visão geral: cada verba oferece "Registrar gasto" ali.
        payload.put("path", "/");
        deliver(userId, payload);
    }

    private int deliver(UUID userId, Map<String, Object> payload) {
        String body = json.writeValueAsString(payload);
        int failures = 0;
        for (WebPushSubscription subscription : subscriptions.findByUserId(userId)) {
            WebPushSender.Outcome outcome = sender.send(
                    new WebPushSender.Target(subscription.endpoint(), subscription.p256dh(), subscription.auth()), body);
            switch (outcome) {
                case DELIVERED -> subscription.recordSuccess(clock.instant());
                case GONE -> {
                    subscriptions.delete(subscription);
                    log.info("web_push_subscription_expired subscriptionId={} userId={}", subscription.id(), userId);
                }
                case FAILED -> failures++;
            }
        }
        log.info("web_push_dispatched userId={} category={} failures={}", userId, payload.get("category"), failures);
        return failures;
    }
}
