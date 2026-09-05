package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recovery_code")
public class RecoveryCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected RecoveryCode() {
    }

    private RecoveryCode(UUID userId, String codeHash, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.createdAt = Objects.requireNonNull(now);
    }

    public static RecoveryCode issue(UUID userId, String codeHash, Instant now) {
        return new RecoveryCode(userId, codeHash, now);
    }

    public boolean canBeConsumedAt(Instant now) {
        return consumedAt == null && invalidatedAt == null;
    }

    public void consume(Instant now) {
        if (!canBeConsumedAt(now)) {
            throw new IllegalStateException("Código de recuperação indisponível");
        }
        consumedAt = now;
    }

    public void invalidate(Instant now) {
        if (consumedAt == null && invalidatedAt == null) {
            invalidatedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public Instant invalidatedAt() {
        return invalidatedAt;
    }
}
