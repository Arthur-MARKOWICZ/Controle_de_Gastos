package br.com.controlegastos.app

/**
 * Decisão do [DailyReminderWorker] sem Android Framework (testável na JVM).
 * Spec: ui-mobile/lembrete-diario — notifica só com `hasExpense == false`,
 * sessão válida e permissão; sem rede/sessão não notifica.
 */
internal data class DailyReminderPlan(
    val shouldNotify: Boolean,
    val shouldReschedule: Boolean,
)

internal object DailyReminderWorkerLogic {
    /** Extra que a [MainActivity] lê para abrir o lançamento rápido ao toque. */
    const val QUICK_EXPENSE_EXTRA = MainActivity.EXTRA_QUICK_EXPENSE

    fun plan(
        reminderTime: String?,
        hasStoredSession: Boolean,
        hasExpenseToday: Boolean?,
        canNotify: Boolean,
    ): DailyReminderPlan {
        if (reminderTime.isNullOrBlank()) {
            return DailyReminderPlan(shouldNotify = false, shouldReschedule = false)
        }
        if (!hasStoredSession) {
            return DailyReminderPlan(shouldNotify = false, shouldReschedule = false)
        }
        return DailyReminderPlan(
            shouldNotify = hasExpenseToday == false && canNotify,
            shouldReschedule = true,
        )
    }
}
