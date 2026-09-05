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
@Table(name = "mfa_login_challenge")
public class MfaLoginChallenge {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
    private String challengeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected MfaLoginChallenge() {
    }

    private MfaLoginChallenge(UUID userId, String challengeHash, Instant now, Duration lifetime) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.challengeHash = Objects.requireNonNull(challengeHash);
        this.createdAt = Objects.requireNonNull(now);
        this.expiresAt = now.plus(Objects.requireNonNull(lifetime));
    }

    public static MfaLoginChallenge issue(UUID userId, String challengeHash, Instant now, Duration lifetime) {
        return new MfaLoginChallenge(userId, challengeHash, now, lifetime);
    }

    public boolean canBeConsumedAt(Instant now) {
        return consumedAt == null && invalidatedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        if (!canBeConsumedAt(now)) {
            throw new IllegalStateException("Desafio de MFA indisponível");
        }
        consumedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String challengeHash() {
        return challengeHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
