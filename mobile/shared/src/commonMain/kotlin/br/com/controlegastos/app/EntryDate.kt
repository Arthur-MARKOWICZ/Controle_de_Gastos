package br.com.controlegastos.app

/**
 * Data proposta para o lançamento, a partir do mês que está na tela.
 *
 * O painel e o histórico navegam por mês, então a folha não pode presumir
 * hoje: a data precisa pertencer ao mês visto e nunca ser futura, porque a
 * API recusa `occurredAt` adiante de hoje.
 *
 * @return `null` quando o mês visto é futuro e não existe data válida nele.
 */
internal fun defaultOccurredAt(viewed: YearMonth, today: String): String? {
    val currentMonth = monthOf(today)
    return when {
        viewed > currentMonth -> null
        viewed == currentMonth -> today
        else -> viewed.lastDay()
    }
}

/** Indica se o mês visto aceita lançamento — mês futuro não tem data válida. */
internal fun canRegisterIn(viewed: YearMonth, today: String): Boolean =
    defaultOccurredAt(viewed, today) != null

/** Mês de competência da data `YYYY-MM-DD` informada pela plataforma. */
internal fun monthOf(isoDate: String): YearMonth =
    YearMonth(isoDate.substring(0, 4).toInt(), isoDate.substring(5, 7).toInt())

/**
 * Último dia que a folha aceita no mês visto.
 *
 * No mês corrente a borda é hoje, porque a API recusa data futura; num mês
 * passado é o último dia daquele mês.
 */
internal fun lastSelectableEntryDay(viewed: YearMonth, today: String): String =
    minOf(today, viewed.lastDay())

/** A data precisa cair no mês visto e não ser futura. */
internal fun isSelectableEntryDate(date: String, viewed: YearMonth, today: String): Boolean =
    isIsoDate(date) && date >= viewed.firstDay() && date <= lastSelectableEntryDay(viewed, today)

/**
 * Atalhos de data oferecidos pela folha, no máximo dois.
 *
 * Só entra o que o mês visto aceita: num mês passado "Hoje" não existe, e num
 * mês futuro nenhuma data é proposta, porque nenhuma é válida.
 */
internal fun entryDateShortcuts(viewed: YearMonth, today: String): List<Pair<String, String>> =
    listOf(
        "Hoje" to today,
        "Ontem" to previousIsoDay(today),
        "Último dia do mês" to viewed.lastDay(),
    ).filter { isSelectableEntryDate(it.second, viewed, today) }.take(2)
