package br.com.controlegastos.app

data class MoneyView(val amount: String)

data class GoalProgressView(
    val plannedAmount: MoneyView,
    val contributedAmount: MoneyView,
    val remainingAmount: MoneyView,
    val percent: Int,
)

data class EnvelopeView(
    val id: String,
    val name: String,
    val purpose: String,
    val baseAmount: MoneyView,
    val available: MoneyView,
    val isNegative: Boolean,
    val goalProgress: GoalProgressView? = null,
)

data class FinancialDashboard(
    val income: MoneyView?,
    val allocated: MoneyView,
    val unallocated: MoneyView,
    val usagePct: Double,
    val envelopes: List<EnvelopeView>,
)

interface FinanceGateway {
    suspend fun loadDashboard(): FinancialDashboard
}

object UnavailableFinanceGateway : FinanceGateway {
    override suspend fun loadDashboard(): FinancialDashboard = error("Dados financeiros não configurados para esta plataforma")
}

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Content(val dashboard: FinancialDashboard) : DashboardState
    data class Error(val message: String) : DashboardState
}

class FinanceDashboardController(private val gateway: FinanceGateway) {
    var state: DashboardState = DashboardState.Loading
        private set

    suspend fun refresh() {
        state = DashboardState.Loading
        state = try {
            DashboardState.Content(gateway.loadDashboard())
        } catch (_: Throwable) {
            DashboardState.Error("Não foi possível carregar suas verbas.")
        }
    }
}
