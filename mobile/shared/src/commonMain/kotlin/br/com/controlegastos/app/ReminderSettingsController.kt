package br.com.controlegastos.app

data class ReminderSettingsState(
    val preferences: NotificationPreferencesView? = null,
    val permissionDenied: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val enabled: Boolean get() = preferences?.dailyReminder == true
    val time: String get() = preferences?.reminderTime ?: "20:00"
}

/**
 * Lembrete diário local (spec ui-mobile/lembrete-diario): o horário e o
 * consentimento ficam na API; o aparelho só agenda. Sem permissão de
 * notificação, a escolha continua salva, mas nada é agendado.
 */
class ReminderSettingsController(
    private val gateway: NotificationGateway,
    private val scheduler: ReminderScheduler,
) {
    var state = ReminderSettingsState()
        private set

    suspend fun load() {
        state = state.copy(busy = true, error = null)
        state = runCatching { gateway.loadNotificationPreferences() }
            .fold(
                onSuccess = { state.copy(preferences = it, busy = false) },
                onFailure = { state.copy(busy = false, error = describeFailure(it, LOAD_FAILURE)) },
            )
    }

    /** Ao abrir o app: segue o que está na API, sem pedir permissão. */
    suspend fun sync() {
        val preferences = runCatching { gateway.loadNotificationPreferences() }.getOrNull() ?: return
        state = state.copy(preferences = preferences)
        apply(preferences)
    }

    suspend fun setEnabled(enabled: Boolean) {
        val current = state.preferences ?: return
        var denied = false
        if (enabled && !scheduler.canNotify()) {
            denied = !scheduler.requestPermission()
        }
        save(current.copy(dailyReminder = enabled), permissionDenied = enabled && denied)
    }

    suspend fun setTime(time: String) {
        val current = state.preferences ?: return
        save(current.copy(reminderTime = time), permissionDenied = state.permissionDenied)
    }

    /** No logout, nenhum lembrete fica agendado no aparelho. */
    fun signOut() = scheduler.cancel()

    private suspend fun save(next: NotificationPreferencesView, permissionDenied: Boolean) {
        val previous = state.preferences
        state = state.copy(preferences = next, busy = true, error = null)
        runCatching { gateway.saveNotificationPreferences(next) }
            .onSuccess { saved ->
                state = state.copy(preferences = saved, busy = false, permissionDenied = permissionDenied)
                apply(saved)
            }
            .onFailure { cause ->
                state = state.copy(preferences = previous, busy = false, error = describeFailure(cause, SAVE_FAILURE))
            }
    }

    private fun apply(preferences: NotificationPreferencesView) {
        if (preferences.dailyReminder && scheduler.canNotify()) {
            scheduler.schedule(preferences.reminderTime)
        } else {
            scheduler.cancel()
        }
    }

    private companion object {
        const val LOAD_FAILURE = "Não foi possível carregar o lembrete."
        const val SAVE_FAILURE = "Não foi possível salvar o lembrete. Tente de novo."
    }
}
