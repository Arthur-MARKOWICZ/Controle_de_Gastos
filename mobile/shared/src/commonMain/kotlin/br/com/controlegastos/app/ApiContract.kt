package br.com.controlegastos.app

/**
 * Falha devolvida pela API, preservando o `code` estável do `problem+json`.
 *
 * O código permite que a tela explique o caso concreto — verba acima da renda,
 * renda abaixo do já alocado — em vez de uma mensagem genérica de erro.
 */
class ApiException(
    val status: Int,
    val code: String,
    val detail: String,
) : RuntimeException(detail.ifBlank { code })

/** Códigos de `problem+json` que a interface trata de forma específica. */
object ApiErrorCode {
    const val ALLOCATION_EXCEEDS_INCOME = "ALLOCATION_EXCEEDS_INCOME"
    const val INCOME_BELOW_BASE_ALLOCATIONS = "INCOME_BELOW_BASE_ALLOCATIONS"
    const val INCOME_CONCURRENT_CHANGE = "INCOME_CONCURRENT_CHANGE"
    const val INCOME_NOT_CONFIGURED = "INCOME_NOT_CONFIGURED"
    const val ENVELOPE_NOT_FOUND = "ENVELOPE_NOT_FOUND"
    const val LEDGER_ENTRY_NOT_FOUND = "LEDGER_ENTRY_NOT_FOUND"
    const val FORBIDDEN = "FORBIDDEN"
    const val LAST_LOGIN_METHOD = "LAST_LOGIN_METHOD"
    const val PASSWORD_ALREADY_SET = "PASSWORD_ALREADY_SET"
    const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
}

/** A sessão acabou: nem o access token nem o refresh valem mais. */
fun Throwable.isSessionExpired(): Boolean = this is ApiException && status == 401

/**
 * Traduz uma falha para uma frase curta em português.
 *
 * Mensagens de erro não citam valores de outras verbas nem dados de terceiros;
 * o detalhe do servidor só é usado quando o código é conhecido.
 */
fun describeFailure(cause: Throwable, fallback: String): String = when {
    cause !is ApiException -> fallback
    cause.code == ApiErrorCode.ALLOCATION_EXCEEDS_INCOME ->
        "A soma das verbas passaria da sua renda do mês. Reduza o valor ou ajuste a renda."
    cause.code == ApiErrorCode.INCOME_BELOW_BASE_ALLOCATIONS ->
        "A renda informada é menor do que o total já reservado nas suas verbas."
    cause.code == ApiErrorCode.INCOME_CONCURRENT_CHANGE ->
        "A renda mudou em outro dispositivo. Recarregue e tente de novo."
    cause.code == ApiErrorCode.INCOME_NOT_CONFIGURED ->
        "Você ainda não configurou a renda deste mês."
    cause.code == ApiErrorCode.ENVELOPE_NOT_FOUND -> "Esta verba não existe mais."
    cause.code == ApiErrorCode.LEDGER_ENTRY_NOT_FOUND -> "Este lançamento não existe mais."
    cause.code == ApiErrorCode.FORBIDDEN -> "Só quem criou a verba pode fazer isso."
    cause.code == ApiErrorCode.TOO_MANY_ATTEMPTS -> "Tentativas demais. Espere um pouco e tente de novo."
    cause.status == 401 -> "Sua sessão expirou. Entre novamente."
    cause.detail.isNotBlank() -> cause.detail
    else -> fallback
}
