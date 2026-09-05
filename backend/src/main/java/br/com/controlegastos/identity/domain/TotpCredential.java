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
@Table(name = "totp_credential")
public class TotpCredential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TotpCredentialStatus status;

    @Column(name = "secret_ciphertext")
    private byte[] secretCiphertext;

    @Column(name = "secret_nonce")
    private byte[] secretNonce;

    @Column(name = "key_version")
    private Integer keyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "pending_expires_at")
    private Instant pendingExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TotpCredential() {
    }

    private TotpCredential(UUID userId, Instant now) {
        this.userId = Objects.requireNonNull(userId);
        this.status = TotpCredentialStatus.DISABLED;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static TotpCredential initiallyDisabled(UUID userId, Instant now) {
        return new TotpCredential(userId, now);
    }

    public void startEnrollment(byte[] ciphertext, byte[] nonce, int keyVersion, Instant now, Duration pendingLifetime) {
        if (status == TotpCredentialStatus.ENABLED) {
            throw new IllegalStateException("MFA já está ativo; desabilite antes de configurar novamente");
        }
        this.secretCiphertext = Objects.requireNonNull(ciphertext);
        this.secretNonce = Objects.requireNonNull(nonce);
        this.keyVersion = keyVersion;
        this.status = TotpCredentialStatus.PENDING;
        this.confirmedAt = null;
        this.pendingExpiresAt = now.plus(Objects.requireNonNull(pendingLifetime));
        this.updatedAt = now;
    }

    public boolean canConfirmAt(Instant now) {
        return status == TotpCredentialStatus.PENDING
                && pendingExpiresAt != null
                && now.isBefore(pendingExpiresAt);
    }

    public void confirm(Instant now) {
        if (!canConfirmAt(now)) {
            throw new IllegalStateException("Configuração de MFA indisponível ou expirada");
        }
        this.status = TotpCredentialStatus.ENABLED;
        this.confirmedAt = now;
        this.pendingExpiresAt = null;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.status = TotpCredentialStatus.DISABLED;
        this.secretCiphertext = null;
        this.secretNonce = null;
        this.keyVersion = null;
        this.confirmedAt = null;
        this.pendingExpiresAt = null;
        this.updatedAt = now;
    }

    public boolean requiresMfaAtLogin() {
        return status == TotpCredentialStatus.ENABLED;
    }

    public UUID userId() {
        return userId;
    }

    public TotpCredentialStatus status() {
        return status;
    }

    public byte[] secretCiphertext() {
        return secretCiphertext;
    }

    public byte[] secretNonce() {
        return secretNonce;
    }

    public Integer keyVersion() {
        return keyVersion;
    }

    public Instant pendingExpiresAt() {
        return pendingExpiresAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }
}
