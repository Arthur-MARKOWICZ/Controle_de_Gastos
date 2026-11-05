package br.com.controlegastos.app

/** Alerta persistente in-app (sem valores monetários). */
data class InAppAlertView(
    val id: String,
    val type: String,
    val envelopeId: String,
    val envelopeName: String,
    val referenceMonth: String,
    val createdAt: String,
)

interface AlertGateway {
    suspend fun listActiveAlerts(): List<InAppAlertView>
    suspend fun dismissAlert(id: String)
}
