package br.com.controlegastos.app

import android.content.Context
import android.content.Intent

/** Payload do deep link (testável sem Context/Robolectric). */
data class QuickExpenseDeepLink(
    val quickExpense: Boolean = true,
    val envelopeId: String? = null,
) {
    companion object {
        fun forEnvelope(envelopeId: String?) =
            QuickExpenseDeepLink(envelopeId = envelopeId?.takeIf { it.isNotBlank() })

        fun generic() = QuickExpenseDeepLink()
    }
}

/** Montagem dos deep links do widget e do tile (sem depender de Glance no teste). */
object QuickExpenseIntents {

    fun openQuickExpense(context: Context, envelopeId: String? = null): Intent {
        val link = QuickExpenseDeepLink.forEnvelope(envelopeId)
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_QUICK_EXPENSE, link.quickExpense)
            link.envelopeId?.let { putExtra(MainActivity.EXTRA_ENVELOPE_ID, it) }
        }
    }
}

/** Estado apresentado pelo widget após consultar a API (ou falhar). */
sealed interface WidgetSuggestionState {
    data object SignedOut : WidgetSuggestionState
    data class Ready(val items: List<ExpenseSuggestionItem>) : WidgetSuggestionState
}

object WidgetSuggestionLogic {
    fun stateFor(authenticated: Boolean, suggestions: ExpenseSuggestions?): WidgetSuggestionState {
        if (!authenticated) return WidgetSuggestionState.SignedOut
        return WidgetSuggestionState.Ready(suggestions?.items.orEmpty().take(3))
    }
}
