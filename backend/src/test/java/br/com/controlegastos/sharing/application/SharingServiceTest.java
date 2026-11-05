package br.com.controlegastos.sharing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopeParticipant;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.UserDirectory;
import br.com.controlegastos.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SharingServiceTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTICIPANT = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ENVELOPE_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

    @Mock EnvelopeService envelopes;
    @Mock AuthenticationService authentication;
    @Mock UserDirectory users;

    private Envelope ownedEnvelope;
    private SharingService sharing;

    @BeforeEach
    void setUp() {
        ownedEnvelope = Envelope.create(OWNER, "Mercado", EnvelopePurpose.LIMIT, Money.brl("500.00"), null, NOW);
        lenient().when(authentication.currentUserId()).thenReturn(OWNER);
        lenient().when(envelopes.requireOwned(ENVELOPE_ID)).thenReturn(ownedEnvelope);
        sharing = new SharingService(envelopes, authentication, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void invitesVerifiedUserAsParticipant() {
        when(users.findVerifiedByEmail("Amigo@Example.com"))
                .thenReturn(Optional.of(new UserDirectory.VerifiedUser(PARTICIPANT, "amigo@example.com")));
        when(envelopes.isParticipant(ENVELOPE_ID, PARTICIPANT)).thenReturn(false);
        when(envelopes.addParticipant(eq(ENVELOPE_ID), eq(PARTICIPANT), eq(OWNER), eq(NOW)))
                .thenReturn(new EnvelopeParticipant(ENVELOPE_ID, PARTICIPANT, NOW, OWNER));

        SharingService.ParticipantView view = sharing.invite(ENVELOPE_ID, "Amigo@Example.com");

        assertThat(view.userId()).isEqualTo(PARTICIPANT);
        assertThat(view.email()).isEqualTo("amigo@example.com");
        verify(envelopes).addParticipant(ENVELOPE_ID, PARTICIPANT, OWNER, NOW);
    }

    @Test
    void refusesUnknownOrUnverifiedEmailWithGenericError() {
        when(users.findVerifiedByEmail("x@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sharing.invite(ENVELOPE_ID, "x@example.com"))
                .isInstanceOf(SharingInviteException.class)
                .hasMessageContaining("Não foi possível convidar");
        verify(envelopes, never()).addParticipant(any(), any(), any(), any());
    }

    @Test
    void refusesInvitingOwner() {
        when(users.findVerifiedByEmail("dono@example.com"))
                .thenReturn(Optional.of(new UserDirectory.VerifiedUser(OWNER, "dono@example.com")));

        assertThatThrownBy(() -> sharing.invite(ENVELOPE_ID, "dono@example.com"))
                .isInstanceOf(SharingInviteException.class);
        verify(envelopes, never()).addParticipant(any(), any(), any(), any());
    }

    @Test
    void listsParticipantsForOwner() {
        when(envelopes.listParticipants(ENVELOPE_ID)).thenReturn(List.of(
                new EnvelopeParticipant(ENVELOPE_ID, PARTICIPANT, NOW, OWNER)));
        when(users.emailOf(PARTICIPANT)).thenReturn(Optional.of("amigo@example.com"));

        assertThat(sharing.list(ENVELOPE_ID)).singleElement().satisfies(view -> {
            assertThat(view.userId()).isEqualTo(PARTICIPANT);
            assertThat(view.email()).isEqualTo("amigo@example.com");
        });
    }

    @Test
    void removesParticipant() {
        sharing.remove(ENVELOPE_ID, PARTICIPANT);
        verify(envelopes).removeParticipant(ENVELOPE_ID, PARTICIPANT);
    }

    @Test
    void nonOwnerCannotManageSharing() {
        when(envelopes.requireOwned(ENVELOPE_ID))
                .thenThrow(new EnvelopeForbiddenException("Somente o proprietário"));

        assertThatThrownBy(() -> sharing.list(ENVELOPE_ID)).isInstanceOf(EnvelopeForbiddenException.class);
    }
}
