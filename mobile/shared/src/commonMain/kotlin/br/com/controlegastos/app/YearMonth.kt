package br.com.controlegastos.app

private val MONTH_NAMES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)

private val DAYS_IN_MONTH = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/**
 * Mês de competência no formato `YYYY-MM` usado pela API.
 *
 * O mês corrente vem injetado pela plataforma, como os demais recursos que
 * dependem do dispositivo, para o módulo comum não precisar de um relógio.
 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init {
        require(month in 1..12) { "Mês fora do intervalo: $month" }
    }

    fun toApiMonth(): String = "$year-${month.toString().padStart(2, '0')}"

    fun label(): String = "${MONTH_NAMES[month - 1]} de $year"

    fun previous(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)

    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)

    /** Primeiro dia do mês, no formato `YYYY-MM-DD` esperado por `from`. */
    fun firstDay(): String = "${toApiMonth()}-01"

    /** Último dia do mês, no formato `YYYY-MM-DD` esperado por `to`. */
    fun lastDay(): String = "${toApiMonth()}-${lengthOfMonth().toString().padStart(2, '0')}"

    fun lengthOfMonth(): Int = if (month == 2 && isLeapYear()) 29 else DAYS_IN_MONTH[month - 1]

    private fun isLeapYear(): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    override fun compareTo(other: YearMonth): Int =
        compareValuesBy(this, other, YearMonth::year, YearMonth::month)

    companion object {
        private val API_MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

        fun parseOrNull(text: String): YearMonth? {
            if (!API_MONTH.matches(text)) return null
            return YearMonth(text.substring(0, 4).toInt(), text.substring(5, 7).toInt())
        }

        fun parse(text: String): YearMonth =
            requireNotNull(parseOrNull(text)) { "Mês fora do contrato da API: '$text'" }
    }
}
