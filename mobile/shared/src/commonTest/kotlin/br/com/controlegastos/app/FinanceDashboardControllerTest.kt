package br.com.controlegastos.app

import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class FinanceDashboardControllerTest {
    @Test
    fun `uses the server goal percentage for a contribution goal progress bar`() {
        val goal = EnvelopeView(
            "goal-1", "Investimentos", "GOAL", MoneyView("100.00"), MoneyView("120.00"), false,
            GoalProgressView(MoneyView("200.00"), MoneyView("20.00"), MoneyView("180.00"), 10),
        )

        assertEquals(0.1f, progressOf(goal))
    }

    @Test
    fun `loads the financial dashboard from the gateway`() = runSuspend {
        val expected = FinancialDashboard(
            income = MoneyView("5000.00"),
            allocated = MoneyView("4250.00"),
            unallocated = MoneyView("750.00"),
            usagePct = 85.0,
            envelopes = listOf(EnvelopeView("envelope-1", "Combustível", "LIMIT", MoneyView("400.00"), MoneyView("240.00"), false)),
        )
        val controller = FinanceDashboardController(FakeFinanceGateway(expected))

        controller.refresh()

        assertEquals(DashboardState.Content(expected), controller.state)
    }

    private class FakeFinanceGateway(private val dashboard: FinancialDashboard) : FinanceGateway {
        override suspend fun loadDashboard(): FinancialDashboard = dashboard
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(object : kotlin.coroutines.Continuation<Unit> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
    })
    failure?.let { throw it }
}
