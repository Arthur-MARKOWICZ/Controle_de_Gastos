package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "invalidated_at") private Instant invalidatedAt;

    protected PasswordResetToken() { }

    private PasswordResetToken(UUID userId, String tokenHash, Instant now, Duration lifetime) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.createdAt = Objects.requireNonNull(now);
        this.expiresAt = now.plus(Objects.requireNonNull(lifetime));
    }

    public static PasswordResetToken issue(UUID userId, String tokenHash, Instant now, Duration lifetime) {
        return new PasswordResetToken(userId, tokenHash, now, lifetime);
    }

    public boolean canBeConsumedAt(Instant now) {
        return consumedAt == null && invalidatedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        if (!canBeConsumedAt(now)) throw new IllegalStateException("Token de recuperação indisponível");
        consumedAt = now;
    }

    public UUID userId() { return userId; }
    public String tokenHash() { return tokenHash; }
}
