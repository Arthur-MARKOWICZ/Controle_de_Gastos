package br.com.controlegastos.app

/** Preferências de notificação da conta (ADR-0033). Horário em `HH:mm`, fuso de Brasília. */
data class NotificationPreferencesView(
    val dailyReminder: Boolean,
    val negativeBalance: Boolean,
    val sharedExpense: Boolean,
    val reminderTime: String,
)

interface NotificationGateway {
    suspend fun loadNotificationPreferences(): NotificationPreferencesView
    suspend fun saveNotificationPreferences(preferences: NotificationPreferencesView): NotificationPreferencesView

    /** A pessoa já registrou algum gasto hoje (data de negócio do backend)? */
    suspend fun hasExpenseToday(): Boolean
}

/**
 * Agenda o lembrete diário local no aparelho. No Android usa WorkManager; no iOS,
 * sem cliente HTTP ainda (ADR-0004), é [NoReminderScheduler].
 */
interface ReminderScheduler {
    /** As notificações já podem ser exibidas, sem pedir nada à pessoa? */
    fun canNotify(): Boolean

    /** Pede a permissão de notificação, quando o sistema exige. */
    suspend fun requestPermission(): Boolean

    fun schedule(time: String)

    fun cancel()
}

object NoReminderScheduler : ReminderScheduler {
    override fun canNotify() = false
    override suspend fun requestPermission() = false
    override fun schedule(time: String) = Unit
    override fun cancel() = Unit
}

/** Horários oferecidos: passos de 15 minutos, como a API aceita. */
val REMINDER_TIMES: List<String> = (0 until 96).map { index ->
    "${(index / 4).toString().padStart(2, '0')}:${((index % 4) * 15).toString().padStart(2, '0')}"
}

fun isReminderTime(value: String): Boolean = value in REMINDER_TIMES

/** Minutos até o próximo lembrete; se o horário de hoje já passou (ou é agora), vale o de amanhã. */
fun minutesUntilNextReminder(nowMinuteOfDay: Int, reminderTime: String): Int {
    require(isReminderTime(reminderTime)) { "Horário de lembrete inválido" }
    val (hour, minute) = reminderTime.split(":").map(String::toInt)
    val target = hour * 60 + minute
    val delta = target - nowMinuteOfDay
    return if (delta > 0) delta else delta + 24 * 60
}
