package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Embedded
    private PasswordCredential passwordCredential;

    protected UserAccount() {
    }

    private UserAccount(UUID id, String emailNormalized, String passwordHash, Instant now) {
        this.id = id;
        this.emailNormalized = emailNormalized;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.passwordCredential = new PasswordCredential(passwordHash, now);
    }

    public static UserAccount register(EmailAddress email, String passwordHash, Instant now) {
        return new UserAccount(UUID.randomUUID(), email.value(), passwordHash, now);
    }

    public UUID id() {
        return id;
    }

    public String emailNormalized() {
        return emailNormalized;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public PasswordCredential passwordCredential() {
        return passwordCredential;
    }

    public void block(Instant now) {
        status = UserStatus.BLOCKED;
        updatedAt = now;
    }
}
