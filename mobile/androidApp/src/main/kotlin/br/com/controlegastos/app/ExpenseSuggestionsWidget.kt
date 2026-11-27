package br.com.controlegastos.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExpenseSuggestionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { loadState(context) }
        provideContent {
            GlanceTheme {
                WidgetContent(context, state)
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetSuggestionState {
        val client = SharedApiClient.get(context.applicationContext)
        val gateway = AndroidApiGateway(client, context.applicationContext.cacheDir)
        val session = runCatching { gateway.restore() }.getOrNull()
        if (session == null) return WidgetSuggestionState.SignedOut
        val suggestions = runCatching { gateway.loadExpenseSuggestions() }.getOrNull()
        return WidgetSuggestionLogic.stateFor(authenticated = true, suggestions = suggestions)
    }
}

@Composable
private fun WidgetContent(context: Context, state: WidgetSuggestionState) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Verbas",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        )
        when (state) {
            WidgetSuggestionState.SignedOut -> {
                Text(
                    text = "Abra o app e entre para ver atalhos.",
                    modifier = GlanceModifier.padding(top = 8.dp),
                )
                Text(
                    text = "Abrir app",
                    modifier = GlanceModifier.padding(top = 8.dp)
                        .clickable(actionStartActivity(QuickExpenseIntents.openQuickExpense(context))),
                    style = TextStyle(fontWeight = FontWeight.Medium),
                )
            }
            is WidgetSuggestionState.Ready -> {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)
                        .clickable(actionStartActivity(QuickExpenseIntents.openQuickExpense(context))),
                ) {
                    Text(text = "+ gasto", style = TextStyle(fontWeight = FontWeight.Medium))
                }
                state.items.forEach { item ->
                    Text(
                        text = item.name,
                        modifier = GlanceModifier.padding(top = 6.dp)
                            .clickable(
                                actionStartActivity(
                                    QuickExpenseIntents.openQuickExpense(context, item.envelopeId),
                                ),
                            ),
                    )
                }
                if (state.items.isEmpty()) {
                    Text(
                        text = "Sem padrão recente. Toque em + gasto.",
                        modifier = GlanceModifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

class ExpenseSuggestionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseSuggestionsWidget()
}
