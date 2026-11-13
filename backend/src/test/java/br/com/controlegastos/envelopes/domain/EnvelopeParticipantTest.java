package br.com.controlegastos.envelopes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvelopeParticipantTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");
    private final UUID envelope = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @Test
    @DisplayName("registra verba, participante, data e quem convidou")
    void registraParticipante() {
        EnvelopeParticipant participant = new EnvelopeParticipant(envelope, user, NOW, owner);

        assertThat(participant.envelopeId()).isEqualTo(envelope);
        assertThat(participant.userId()).isEqualTo(user);
        assertThat(participant.addedAt()).isEqualTo(NOW);
        assertThat(participant.addedBy()).isEqualTo(owner);
    }

    @Test
    @DisplayName("recusa participante sem verba")
    void recusaSemVerba() {
        assertThatThrownBy(() -> new EnvelopeParticipant(null, user, NOW, owner)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("recusa participante sem usuário")
    void recusaSemUsuario() {
        assertThatThrownBy(() -> new EnvelopeParticipant(envelope, null, NOW, owner)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("recusa participante sem data de inclusão")
    void recusaSemData() {
        assertThatThrownBy(() -> new EnvelopeParticipant(envelope, user, null, owner)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("recusa participante sem autor do convite")
    void recusaSemAutor() {
        assertThatThrownBy(() -> new EnvelopeParticipant(envelope, user, NOW, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("chave composta é igual para a mesma verba e usuário")
    void chaveCompostaIgual() {
        assertThat(new EnvelopeParticipant.EnvelopeParticipantId(envelope, user))
                .isEqualTo(new EnvelopeParticipant.EnvelopeParticipantId(envelope, user))
                .hasSameHashCodeAs(new EnvelopeParticipant.EnvelopeParticipantId(envelope, user));
    }

    @Test
    @DisplayName("mesmo usuário em outra verba é outro participante")
    void outraVerbaOutraChave() {
        assertThat(new EnvelopeParticipant.EnvelopeParticipantId(envelope, user))
                .isNotEqualTo(new EnvelopeParticipant.EnvelopeParticipantId(UUID.randomUUID(), user));
    }
}
