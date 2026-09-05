package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "oauth_authorization_state")
public class OAuthAuthorizationState {

    @Id
    private UUID id;

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "linking_user_id")
    private UUID linkingUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthAuthorizationState() {
    }

    private OAuthAuthorizationState(String stateHash, OAuthProvider provider, UUID linkingUserId,
                                     Instant now, Duration lifetime) {
        this.id = UUID.randomUUID();
        this.stateHash = Objects.requireNonNull(stateHash);
        this.provider = Objects.requireNonNull(provider);
        this.linkingUserId = linkingUserId;
        this.createdAt = Objects.requireNonNull(now);
        this.expiresAt = now.plus(Objects.requireNonNull(lifetime));
    }

    public static OAuthAuthorizationState issue(String stateHash, OAuthProvider provider, UUID linkingUserId,
                                                 Instant now, Duration lifetime) {
        return new OAuthAuthorizationState(stateHash, provider, linkingUserId, now, lifetime);
    }

    public boolean canBeConsumedAt(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        if (!canBeConsumedAt(now)) {
            throw new IllegalStateException("State OAuth indisponível");
        }
        consumedAt = now;
    }

    public UUID id() {
        return id;
    }

    public String stateHash() {
        return stateHash;
    }

    public OAuthProvider provider() {
        return provider;
    }

    public UUID linkingUserId() {
        return linkingUserId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
