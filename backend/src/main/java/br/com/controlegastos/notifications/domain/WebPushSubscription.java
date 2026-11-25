package br.com.controlegastos.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** Assinatura Web Push de um navegador (ADR-0033). O endpoint é único entre contas. */
@Entity
@Table(name = "web_push_subscription")
public class WebPushSubscription {

    private static final int MAX_ENDPOINT_LENGTH = 2048;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, unique = true)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 100)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 40)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    protected WebPushSubscription() {
    }

    private WebPushSubscription(UUID userId, String endpoint, String p256dh, String auth, Instant now) {
        this.id = UUID.randomUUID();
        this.endpoint = requireEndpoint(endpoint);
        this.createdAt = Objects.requireNonNull(now);
        assign(userId, p256dh, auth);
    }

    public static WebPushSubscription create(UUID userId, String endpoint, String p256dh, String auth, Instant now) {
        return new WebPushSubscription(userId, endpoint, p256dh, auth, now);
    }

    /** O mesmo navegador volta a se inscrever, talvez por outra conta: a assinatura passa a ela. */
    public void assign(UUID userId, String p256dh, String auth) {
        this.userId = Objects.requireNonNull(userId);
        this.p256dh = requireKey(p256dh, 65, "p256dh");
        this.auth = requireKey(auth, 16, "auth");
    }

    public void recordSuccess(Instant now) {
        this.lastSuccessAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String endpoint() {
        return endpoint;
    }

    public String p256dh() {
        return p256dh;
    }

    public String auth() {
        return auth;
    }

    private static String requireEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException("Endpoint de push inválido");
        }
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("O endpoint de push deve usar https");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("O endpoint de push deve usar https", e);
        }
        return endpoint;
    }

    private static String requireKey(String value, int expectedBytes, String name) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(Objects.requireNonNull(value));
            if (decoded.length != expectedBytes || (expectedBytes == 65 && decoded[0] != 4)) {
                throw new IllegalArgumentException("Chave " + name + " inválida");
            }
            return value;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Chave " + name + " inválida");
        }
    }
}
