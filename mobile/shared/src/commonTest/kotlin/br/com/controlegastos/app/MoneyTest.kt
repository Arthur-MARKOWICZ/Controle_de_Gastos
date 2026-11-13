package br.com.controlegastos.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun `reads the two decimal places sent by the api`() {
        assertEquals(123456L, Money.fromApiAmount("1234.56").cents)
        assertEquals(0L, Money.fromApiAmount("0.00").cents)
        assertEquals(-4250L, Money.fromApiAmount("-42.50").cents)
    }

    @Test
    fun `writes amounts back with the fixed scale the api requires`() {
        assertEquals("1234.56", Money(123456L).toApiAmount())
        assertEquals("0.00", Money(0L).toApiAmount())
        assertEquals("0.07", Money(7L).toApiAmount())
        assertEquals("-42.50", Money(-4250L).toApiAmount())
    }

    @Test
    fun `survives a round trip without losing centavos`() {
        listOf("0.01", "0.10", "9.99", "1234.56", "99999999.99").forEach {
            assertEquals(it, Money.fromApiAmount(it).toApiAmount())
        }
    }

    @Test
    fun `rejects amounts that are not two place decimals`() {
        listOf("12", "12.5", "12.345", "R$ 12,00", "", "abc", "1,00").forEach {
            assertNull(Money.fromApiAmountOrNull(it), "deveria rejeitar '$it'")
        }
        assertFailsWith<IllegalArgumentException> { Money.fromApiAmount("12.5") }
    }

    @Test
    fun `formats brazilian currency with grouped thousands`() {
        assertEquals("R$ 0,00", Money(0L).toBrl())
        assertEquals("R$ 0,07", Money(7L).toBrl())
        assertEquals("R$ 42,50", Money(4250L).toBrl())
        assertEquals("R$ 1.234,56", Money(123456L).toBrl())
        assertEquals("R$ 1.234.567,89", Money(123456789L).toBrl())
        assertEquals("-R$ 42,50", Money(-4250L).toBrl())
    }

    @Test
    fun `knows when a balance is negative`() {
        assertTrue(Money(-1L).isNegative)
        assertTrue(!Money(0L).isNegative)
    }

    @Test
    fun `keeps only digits while the user types an amount`() {
        assertEquals("1234", AmountInput.sanitize("R$ 12,34"))
        assertEquals("", AmountInput.sanitize("abc"))
        assertEquals("57", AmountInput.sanitize("5o7"))
    }

    @Test
    fun `treats typed digits as centavos`() {
        assertEquals("0.00", AmountInput.toMoney("").toApiAmount())
        assertEquals("0.05", AmountInput.toMoney("5").toApiAmount())
        assertEquals("12.34", AmountInput.toMoney("1234").toApiAmount())
        assertEquals("R$ 12,34", AmountInput.display("1234"))
        assertEquals("R$ 0,00", AmountInput.display(""))
        // Centavos da direita para a esquerda: "50" é cinquenta centavos, não
        // cinquenta reais. É isso que a dica do campo precisa explicar.
        assertEquals("R$ 0,50", AmountInput.display("50"))
    }

    @Test
    fun `caps typed input so it cannot overflow the api scale`() {
        val typed = AmountInput.sanitize("9".repeat(30))
        assertEquals(AmountInput.MAX_DIGITS, typed.length)
        assertTrue(AmountInput.toMoney(typed).cents > 0)
    }
}

/**
 * Prévia do saldo após um gasto, calculada só para mostrar antes de confirmar.
 *
 * O saldo que vale é o que a API devolve depois do lançamento; esta conta
 * existe para a pessoa decidir com a informação na tela.
 */
class BalancePreviewTest {
    @Test
    fun `subtracts the expense from what the envelope still has`() {
        assertEquals(
            "-5.00",
            balanceAfterExpense(Money.fromApiAmount("10.00"), Money.fromApiAmount("15.00")).toApiAmount(),
        )
        assertEquals(
            "95.50",
            balanceAfterExpense(Money.fromApiAmount("120.00"), Money.fromApiAmount("24.50")).toApiAmount(),
        )
    }

    @Test
    fun `keeps the preview negative so the screen can warn without blocking`() {
        assertTrue(balanceAfterExpense(Money.fromApiAmount("10.00"), Money.fromApiAmount("15.00")).isNegative)
        assertEquals("R$ -5,00".replace("R$ -", "-R$ "), balanceAfterExpense(Money(1000), Money(1500)).toBrl())
    }

    @Test
    fun `subtracts centavos without going through floating point`() {
        assertEquals("0.01", (Money(10L) - Money(9L)).toApiAmount())
        assertEquals("-0.01", (Money(9L) - Money(10L)).toApiAmount())
    }
}
