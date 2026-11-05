package br.com.controlegastos.sharing.application;

import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopeParticipant;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharingService {

    private static final Logger log = LoggerFactory.getLogger(SharingService.class);
    private static final String GENERIC_INVITE_FAILURE =
            "Não foi possível convidar este e-mail. Confira se a pessoa já tem conta verificada.";

    private final EnvelopeService envelopes;
    private final AuthenticationService authentication;
    private final UserDirectory users;
    private final Clock clock;

    public SharingService(EnvelopeService envelopes, AuthenticationService authentication,
                          UserDirectory users, Clock clock) {
        this.envelopes = envelopes;
        this.authentication = authentication;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public ParticipantView invite(UUID envelopeId, String rawEmail) {
        Envelope envelope = envelopes.requireOwned(envelopeId);
        UUID ownerId = authentication.currentUserId();
        UserDirectory.VerifiedUser invitee = users.findVerifiedByEmail(rawEmail)
                .orElseThrow(() -> new SharingInviteException(GENERIC_INVITE_FAILURE));
        if (invitee.id().equals(envelope.ownerId()) || invitee.id().equals(ownerId)) {
            throw new SharingInviteException("Não é possível convidar o proprietário da verba");
        }
        if (envelopes.isParticipant(envelopeId, invitee.id())) {
            return new ParticipantView(invitee.id(), invitee.email(), clock.instant());
        }
        Instant now = clock.instant();
        EnvelopeParticipant saved = envelopes.addParticipant(envelopeId, invitee.id(), ownerId, now);
        log.info("envelope_participant_added envelopeId={} participantId={} ownerId={}",
                envelopeId, invitee.id(), ownerId);
        return new ParticipantView(saved.userId(), invitee.email(), saved.addedAt());
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> list(UUID envelopeId) {
        envelopes.requireOwned(envelopeId);
        return envelopes.listParticipants(envelopeId).stream()
                .map(participant -> new ParticipantView(
                        participant.userId(),
                        users.emailOf(participant.userId()).orElse("desconhecido"),
                        participant.addedAt()))
                .toList();
    }

    @Transactional
    public void remove(UUID envelopeId, UUID participantId) {
        envelopes.requireOwned(envelopeId);
        envelopes.removeParticipant(envelopeId, participantId);
        log.info("envelope_participant_removed envelopeId={} participantId={} ownerId={}",
                envelopeId, participantId, authentication.currentUserId());
    }

    public record ParticipantView(UUID userId, String email, Instant addedAt) {
    }
}
