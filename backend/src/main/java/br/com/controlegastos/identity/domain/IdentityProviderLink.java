package br.com.controlegastos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "identity_provider_link")
public class IdentityProviderLink {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 254)
    private String providerEmail;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    protected IdentityProviderLink() {
    }

    private IdentityProviderLink(UUID userId, OAuthProvider provider, String providerUserId,
                                  String providerEmail, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.provider = Objects.requireNonNull(provider);
        this.providerUserId = Objects.requireNonNull(providerUserId);
        this.providerEmail = providerEmail;
        this.linkedAt = Objects.requireNonNull(now);
    }

    public static IdentityProviderLink link(UUID userId, OAuthProvider provider, String providerUserId,
                                             String providerEmail, Instant now) {
        return new IdentityProviderLink(userId, provider, providerUserId, providerEmail, now);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public OAuthProvider provider() {
        return provider;
    }

    public String providerUserId() {
        return providerUserId;
    }

    public String providerEmail() {
        return providerEmail;
    }

    public Instant linkedAt() {
        return linkedAt;
    }
}
