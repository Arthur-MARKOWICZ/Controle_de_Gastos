package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "refresh_secret_hash", nullable = false, length = 64)
    private String refreshSecretHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 32)
    private String revocationReason;

    protected AuthSession() {
    }

    private AuthSession(
            UUID id,
            UUID userId,
            String refreshSecretHash,
            Instant createdAt,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.refreshSecretHash = refreshSecretHash;
        this.createdAt = createdAt;
        this.lastUsedAt = createdAt;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public static AuthSession start(
            UUID userId,
            String refreshSecretHash,
            Instant now,
            Duration idleLifetime,
            Duration absoluteLifetime
    ) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(refreshSecretHash);
        return new AuthSession(
                UUID.randomUUID(),
                userId,
                refreshSecretHash,
                now,
                now.plus(idleLifetime),
                now.plus(absoluteLifetime)
        );
    }

    public void rotate(String newRefreshSecretHash, Instant now, Duration idleLifetime) {
        if (!isActiveAt(now)) {
            throw new IllegalStateException("Sessão expirada ou revogada");
        }
        refreshSecretHash = Objects.requireNonNull(newRefreshSecretHash);
        lastUsedAt = now;
        Instant renewedIdleLimit = now.plus(idleLifetime);
        idleExpiresAt = renewedIdleLimit.isBefore(absoluteExpiresAt) ? renewedIdleLimit : absoluteExpiresAt;
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revocationReason = reason;
        }
    }

    public boolean isActiveAt(Instant instant) {
        return revokedAt == null && instant.isBefore(idleExpiresAt) && instant.isBefore(absoluteExpiresAt);
    }

    public boolean matchesRefreshHash(String candidateHash) {
        if (candidateHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                refreshSecretHash.getBytes(StandardCharsets.US_ASCII),
                candidateHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant idleExpiresAt() {
        return idleExpiresAt;
    }

    public Instant absoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
