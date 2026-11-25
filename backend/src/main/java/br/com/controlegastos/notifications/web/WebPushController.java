package br.com.controlegastos.notifications.web;

import br.com.controlegastos.notifications.application.WebPushSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/web-push")
class WebPushController {

    private final WebPushSubscriptionService subscriptions;

    WebPushController(WebPushSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** {@code publicKey} nulo indica que o envio está desligado neste ambiente. */
    @GetMapping("/public-key")
    PublicKeyResponse publicKey() {
        return new PublicKeyResponse(subscriptions.publicKey());
    }

    @PutMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@RequestBody SubscriptionRequest request) {
        if (request.keys() == null) {
            throw new IllegalArgumentException("Chaves da assinatura ausentes");
        }
        subscriptions.register(request.endpoint(), request.keys().p256dh(), request.keys().auth());
    }

    /** POST com corpo para o endpoint não aparecer na URL nem em logs de acesso. */
    @PostMapping("/subscriptions/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@RequestBody RemoveRequest request) {
        subscriptions.remove(request.endpoint());
    }

    record PublicKeyResponse(String publicKey) {
    }

    record SubscriptionRequest(String endpoint, Keys keys) {
        record Keys(String p256dh, String auth) {
        }
    }

    record RemoveRequest(String endpoint) {
    }
}
