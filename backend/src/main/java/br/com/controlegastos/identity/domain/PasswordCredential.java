package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class PasswordCredential {

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    protected PasswordCredential() {
    }

    public PasswordCredential(String passwordHash, Instant passwordChangedAt) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Hash da senha é obrigatório");
        this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "Data de troca da senha é obrigatória");
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant passwordChangedAt() {
        return passwordChangedAt;
    }
}
