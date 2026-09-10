package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Código de uso único que entrega a sessão a um cliente nativo.
 *
 * <p>Só o hash é guardado, e o tempo de vida é curto porque o código transita
 * pela URL do App Link. Ver ADR-020.
 */
@Entity
@Table(name = "oauth_mobile_handoff")
public class OAuthMobileHandoff {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthMobileHandoff() {
    }

    private OAuthMobileHandoff(UUID userId, String codeHash, Instant now, Duration lifetime) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.createdAt = Objects.requireNonNull(now);
        this.expiresAt = now.plus(Objects.requireNonNull(lifetime));
    }

    public static OAuthMobileHandoff issue(UUID userId, String codeHash, Instant now, Duration lifetime) {
        return new OAuthMobileHandoff(userId, codeHash, now, lifetime);
    }

    public boolean canBeConsumedAt(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        if (!canBeConsumedAt(now)) {
            throw new IllegalStateException("Código de handoff indisponível");
        }
        consumedAt = now;
    }

    public UUID userId() {
        return userId;
    }
}
