package br.com.controlegastos.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "in_app_alert")
public class InAppAlert {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "envelope_id", nullable = false)
    private UUID envelopeId;

    @Column(name = "envelope_name", nullable = false, length = 80)
    private String envelopeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private AlertType type;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected InAppAlert() {
    }

    private InAppAlert(UUID id, UUID ownerId, UUID envelopeId, String envelopeName, AlertType type,
                       LocalDate referenceMonth, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.envelopeId = Objects.requireNonNull(envelopeId);
        this.envelopeName = requireName(envelopeName);
        this.type = Objects.requireNonNull(type);
        this.referenceMonth = Objects.requireNonNull(referenceMonth);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static InAppAlert negativeBalance(UUID ownerId, UUID envelopeId, String envelopeName,
                                             YearMonth month, Instant now) {
        return new InAppAlert(
                UUID.randomUUID(),
                ownerId,
                envelopeId,
                envelopeName,
                AlertType.NEGATIVE_BALANCE,
                month.atDay(1),
                now
        );
    }

    public void dismiss(Instant now) {
        if (!isActive()) {
            return;
        }
        this.dismissedAt = Objects.requireNonNull(now);
    }

    public void resolve(Instant now) {
        if (this.resolvedAt != null) {
            return;
        }
        this.resolvedAt = Objects.requireNonNull(now);
    }

    public boolean isActive() {
        return dismissedAt == null && resolvedAt == null;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public UUID envelopeId() {
        return envelopeId;
    }

    public String envelopeName() {
        return envelopeName;
    }

    public AlertType type() {
        return type;
    }

    public LocalDate referenceMonth() {
        return referenceMonth;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant dismissedAt() {
        return dismissedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "O nome da verba é obrigatório");
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("O nome da verba deve ter entre 1 e 80 caracteres");
        }
        return trimmed;
    }
}
