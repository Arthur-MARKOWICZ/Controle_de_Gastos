package br.com.controlegastos.app

/** Participante de uma verba compartilhada (visão do owner). */
data class ParticipantView(
    val userId: String,
    val email: String,
    val addedAt: String,
)

interface SharingGateway {
    suspend fun listParticipants(envelopeId: String): List<ParticipantView>
    suspend fun inviteParticipant(envelopeId: String, email: String): ParticipantView
    suspend fun removeParticipant(envelopeId: String, userId: String)
}
