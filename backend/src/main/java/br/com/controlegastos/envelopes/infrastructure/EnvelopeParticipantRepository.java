package br.com.controlegastos.envelopes.infrastructure;

import br.com.controlegastos.envelopes.domain.EnvelopeParticipant;
import br.com.controlegastos.envelopes.domain.EnvelopeParticipant.EnvelopeParticipantId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvelopeParticipantRepository extends JpaRepository<EnvelopeParticipant, EnvelopeParticipantId> {
    boolean existsByEnvelopeIdAndUserId(UUID envelopeId, UUID userId);
}
