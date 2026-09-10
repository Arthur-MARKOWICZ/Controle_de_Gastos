package br.com.controlegastos.app

import kotlin.jvm.JvmInline

private const val REAL = "R$"

/**
 * Valor em BRL com escala fixa de duas casas, guardado em centavos.
 *
 * A API troca dinheiro como string decimal de duas casas. Manter centavos
 * inteiros evita que um valor passe por ponto flutuante entre ler e devolver.
 */
@JvmInline
value class Money(val cents: Long) {
    val isNegative: Boolean get() = cents < 0

    /** Formato aceito pela API: `^-?\d+\.\d{2}$`. */
    fun toApiAmount(): String = render(unitSeparator = "", decimalSeparator = ".", prefix = "")

    /** Formato de leitura em pt-BR, com ponto de milhar e vírgula decimal. */
    fun toBrl(): String = render(unitSeparator = ".", decimalSeparator = ",", prefix = "$REAL ")

    private fun render(unitSeparator: String, decimalSeparator: String, prefix: String): String {
        val absolute = if (cents < 0) -cents else cents
        val units = (absolute / 100).toString().let { plain ->
            if (unitSeparator.isEmpty()) plain
            else plain.reversed().chunked(3).joinToString(unitSeparator).reversed()
        }
        val centavos = (absolute % 100).toString().padStart(2, '0')
        return "${if (cents < 0) "-" else ""}$prefix$units$decimalSeparator$centavos"
    }

    companion object {
        private val API_AMOUNT = Regex("^-?\\d{1,17}\\.\\d{2}$")

        fun fromApiAmountOrNull(text: String): Money? {
            if (!API_AMOUNT.matches(text)) return null
            val negative = text.startsWith("-")
            val cents = text.removePrefix("-").replace(".", "").toLongOrNull() ?: return null
            return Money(if (negative) -cents else cents)
        }

        fun fromApiAmount(text: String): Money =
            requireNotNull(fromApiAmountOrNull(text)) { "Valor monetário fora do contrato da API: '$text'" }
    }
}

/**
 * Entrada de valor por teclado numérico: o usuário digita centavos da direita
 * para a esquerda, sem precisar posicionar a vírgula.
 */
object AmountInput {
    /** Sustenta até 999.999.999.999,99, dentro das 17 casas aceitas pela API. */
    const val MAX_DIGITS = 14

    fun sanitize(raw: String): String = raw.filter(Char::isDigit).take(MAX_DIGITS)

    fun toMoney(sanitized: String): Money = Money(sanitized.toLongOrNull() ?: 0L)

    fun display(sanitized: String): String = toMoney(sanitized).toBrl()
}
