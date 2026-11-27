package br.com.controlegastos.app

data class ExpenseSuggestionItem(
    val envelopeId: String,
    val name: String,
    val suggestedAmount: Money?,
)

data class ExpenseSuggestions(
    val asOf: String,
    val items: List<ExpenseSuggestionItem>,
)

/**
 * Resolve verba e dígitos de valor para a folha de lançamento.
 *
 * [forcedEnvelopeId] (widget) vence o ranking; se a API não tiver valor para ela,
 * o valor fica vazio. Sem força e sem sugestões, a folha usa a heurística existente.
 */
data class ExpensePrefill(
    val envelopeId: String?,
    val amountDigits: String,
)

fun resolveExpensePrefill(
    suggestions: ExpenseSuggestions?,
    forcedEnvelopeId: String?,
    spendableIds: Set<String>,
): ExpensePrefill {
    if (forcedEnvelopeId != null && forcedEnvelopeId in spendableIds) {
        val match = suggestions?.items?.firstOrNull { it.envelopeId == forcedEnvelopeId }
        return ExpensePrefill(
            envelopeId = forcedEnvelopeId,
            amountDigits = match?.suggestedAmount?.toAmountDigits().orEmpty(),
        )
    }
    val first = suggestions?.items?.firstOrNull { it.envelopeId in spendableIds }
        ?: return ExpensePrefill(envelopeId = null, amountDigits = "")
    return ExpensePrefill(
        envelopeId = first.envelopeId,
        amountDigits = first.suggestedAmount?.toAmountDigits().orEmpty(),
    )
}

fun Money.toAmountDigits(): String = cents.toString()
