package br.com.controlegastos.notifications.application;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.notifications.domain.WebPushSubscription;
import br.com.controlegastos.notifications.infrastructure.WebPushSubscriptionRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro e remoção das assinaturas Web Push da pessoa autenticada (ADR-0033). */
@Service
public class WebPushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(WebPushSubscriptionService.class);

    private final WebPushSubscriptionRepository subscriptions;
    private final AuthenticationService authentication;
    private final WebPushSender sender;
    private final Clock clock;

    public WebPushSubscriptionService(WebPushSubscriptionRepository subscriptions,
                                      AuthenticationService authentication,
                                      WebPushSender sender, Clock clock) {
        this.subscriptions = subscriptions;
        this.authentication = authentication;
        this.sender = sender;
        this.clock = clock;
    }

    public String publicKey() {
        return sender.publicKey();
    }

    @Transactional
    public void register(String endpoint, String p256dh, String auth) {
        UUID userId = authentication.currentUserId();
        WebPushSubscription subscription = subscriptions.findByEndpoint(endpoint)
                .map(existing -> {
                    existing.assign(userId, p256dh, auth);
                    return existing;
                })
                .orElseGet(() -> WebPushSubscription.create(userId, endpoint, p256dh, auth, clock.instant()));
        subscriptions.save(subscription);
        log.info("web_push_subscription_saved subscriptionId={} userId={}", subscription.id(), userId);
    }

    /** Remove só se a assinatura for da pessoa; caso contrário, não faz nada nem revela o dono. */
    @Transactional
    public void remove(String endpoint) {
        if (endpoint == null) {
            return;
        }
        UUID userId = authentication.currentUserId();
        if (subscriptions.deleteOwned(endpoint, userId) > 0) {
            log.info("web_push_subscription_removed userId={}", userId);
        }
    }
}
