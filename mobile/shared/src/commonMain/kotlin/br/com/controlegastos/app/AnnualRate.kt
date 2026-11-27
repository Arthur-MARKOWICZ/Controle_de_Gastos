package br.com.controlegastos.app

/**
 * Converte taxa percentual anual da UI para o decimal com escala 4 da API.
 *
 * Ex.: `"12,50"` → `"0.1250"`. Rejeita vazio, não numérico, `<= 0` ou `> 1000`.
 *
 * Usa aritmética decimal de escala fixa (equivalente a BigDecimal scale 4),
 * sem Double/Float.
 */
fun parseAnnualRatePercentToDecimal(raw: String): String? {
    val cleaned = raw.trim().replace("%", "").replace(",", ".")
    if (cleaned.isEmpty() || !ANNUAL_RATE_PERCENT.matches(cleaned)) return null

    val dot = cleaned.indexOf('.')
    val intPart = if (dot < 0) cleaned else cleaned.substring(0, dot)
    val fracPart = if (dot < 0) "" else cleaned.substring(dot + 1)
    if (fracPart.length > 12) return null

    val intValue = intPart.toLongOrNull() ?: return null
    if (intValue > 1000L) return null
    if (intValue == 1000L && fracPart.any { it != '0' }) return null
    if (intValue == 0L && fracPart.all { it == '0' }) return null

    val unscaled = (intPart + fracPart).trimStart('0').ifEmpty { "0" }
    val scaleAfterDivideBy100 = fracPart.length + 2
    return formatScale4(roundToScale(unscaled, scaleAfterDivideBy100, targetScale = 4))
}

private val ANNUAL_RATE_PERCENT = Regex("^\\d+(\\.\\d+)?$")

private fun roundToScale(digits: String, currentScale: Int, targetScale: Int): String {
    var d = digits.trimStart('0').ifEmpty { "0" }
    when {
        currentScale < targetScale -> return d + "0".repeat(targetScale - currentScale)
        currentScale == targetScale -> return d
        else -> {
            val drop = currentScale - targetScale
            if (d.length <= drop) d = d.padStart(drop + 1, '0')
            val keepLen = d.length - drop
            val keep = d.substring(0, keepLen)
            if (d[keepLen] < '5') return keep.trimStart('0').ifEmpty { "0" }
            return (keep.toLong() + 1L).toString()
        }
    }
}

private fun formatScale4(unscaled: String): String {
    val normalized = unscaled.trimStart('0').ifEmpty { "0" }.padStart(4, '0')
    return if (normalized.length == 4) {
        "0.$normalized"
    } else {
        val split = normalized.length - 4
        "${normalized.substring(0, split)}.${normalized.substring(split)}"
    }
}
