package br.com.controlegastos.envelopes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "envelope_participant")
@IdClass(EnvelopeParticipant.EnvelopeParticipantId.class)
public class EnvelopeParticipant {

    @Id
    @Column(name = "envelope_id", nullable = false)
    private UUID envelopeId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    protected EnvelopeParticipant() {
    }

    public EnvelopeParticipant(UUID envelopeId, UUID userId, Instant addedAt, UUID addedBy) {
        this.envelopeId = Objects.requireNonNull(envelopeId);
        this.userId = Objects.requireNonNull(userId);
        this.addedAt = Objects.requireNonNull(addedAt);
        this.addedBy = Objects.requireNonNull(addedBy);
    }

    public UUID envelopeId() {
        return envelopeId;
    }

    public UUID userId() {
        return userId;
    }

    public Instant addedAt() {
        return addedAt;
    }

    public UUID addedBy() {
        return addedBy;
    }

    public static class EnvelopeParticipantId implements Serializable {
        private UUID envelopeId;
        private UUID userId;

        public EnvelopeParticipantId() {
        }

        public EnvelopeParticipantId(UUID envelopeId, UUID userId) {
            this.envelopeId = envelopeId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EnvelopeParticipantId that)) return false;
            return Objects.equals(envelopeId, that.envelopeId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(envelopeId, userId);
        }
    }
}
