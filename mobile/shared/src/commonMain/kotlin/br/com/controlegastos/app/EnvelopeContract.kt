package br.com.controlegastos.app

/** Natureza da verba, conforme `EnvelopePurpose` do backend. */
enum class EnvelopePurpose(val api: String, val label: String, val explanation: String) {
    LIMIT(
        "LIMIT",
        "Limite de gasto",
        "Teto planejado para um tipo de gasto neste mês. O saldo reinicia no valor-base a cada mês.",
    ),
    FIXED(
        "FIXED",
        "Compromisso fixo",
        "Despesa recorrente de valor previsível, como aluguel. O saldo reinicia a cada mês.",
    ),
    GOAL(
        "GOAL",
        "Meta de aporte",
        "Quanto você pretende guardar por mês. O progresso soma os aportes registrados.",
    ),
    SAVINGS_TARGET(
        "SAVINGS_TARGET",
        "Meta de acumulação",
        "Objetivo com valor-alvo. Acumula até alcançar o total definido.",
    ),
    ANNUAL_EXPENSE(
        "ANNUAL_EXPENSE",
        "Gasto anual",
        "Despesa que vence uma vez por ano e é provisionada até o vencimento.",
    ),
    ;

    companion object {
        /** Propósito desconhecido cai em [LIMIT] apenas para rotular, sem inventar regra. */
        fun fromApiOrNull(api: String): EnvelopePurpose? = entries.firstOrNull { it.api == api }

        fun labelOf(api: String): String = fromApiOrNull(api)?.label ?: api
    }
}

enum class EnvelopeRole { OWNER, PARTICIPANT }

data class GoalProgressView(
    val plannedAmount: Money,
    val contributedAmount: Money,
    val remainingAmount: Money,
    val percent: Int,
)

enum class FundingMode(val api: String, val label: String) {
    MONTHLY("MONTHLY", "Reservar por mês"),
    ONE_TIME("ONE_TIME", "Reservar de uma vez"),
    ;

    companion object {
        fun fromApiOrNull(api: String): FundingMode? = entries.firstOrNull { it.api == api }
    }
}

data class AnnualExpenseView(
    val annualAmount: Money,
    val dueMonth: Int,
    val dueDay: Int,
    val fundingMode: FundingMode,
)

data class EnvelopeView(
    val id: String,
    val name: String,
    val purpose: String,
    val baseAmount: Money,
    val available: Money,
    val isNegative: Boolean,
    val targetAmount: Money? = null,
    val targetReachedAt: String? = null,
    val annualExpense: AnnualExpenseView? = null,
    val goalProgress: GoalProgressView? = null,
    val role: EnvelopeRole = EnvelopeRole.OWNER,
    val archivedAt: String? = null,
) {
    val purposeLabel: String get() = EnvelopePurpose.labelOf(purpose)
    val isOwner: Boolean get() = role == EnvelopeRole.OWNER
}

/**
 * Dados para criar uma verba. Os campos anuais só vão ao corpo quando o
 * propósito é [EnvelopePurpose.ANNUAL_EXPENSE]; o backend rejeita chave extra.
 */
data class NewEnvelope(
    val name: String,
    val purpose: EnvelopePurpose,
    val baseAmount: Money = Money(0),
    val targetAmount: Money? = null,
    val annualAmount: Money? = null,
    val dueMonth: Int? = null,
    val dueDay: Int? = null,
    val fundingMode: FundingMode? = null,
)

/** Alteração parcial de verba: só os campos não nulos são enviados. */
data class EnvelopeEdit(
    val name: String? = null,
    val baseAmount: Money? = null,
    val targetAmount: Money? = null,
    val annualAmount: Money? = null,
    val dueMonth: Int? = null,
    val dueDay: Int? = null,
    val fundingMode: FundingMode? = null,
) {
    val isEmpty: Boolean
        get() = name == null && baseAmount == null && targetAmount == null &&
            annualAmount == null && dueMonth == null && dueDay == null && fundingMode == null
}

interface EnvelopeGateway {
    suspend fun listEnvelopes(month: YearMonth?): List<EnvelopeView>
    suspend fun createEnvelope(envelope: NewEnvelope): EnvelopeView
    suspend fun updateEnvelope(id: String, edit: EnvelopeEdit): EnvelopeView
    suspend fun archiveEnvelope(id: String)
}
