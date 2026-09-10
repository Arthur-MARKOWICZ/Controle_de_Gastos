package br.com.controlegastos.app

/**
 * Resultado do login social devolvido pelo sistema por App Link.
 *
 * O backend redireciona o navegador do sistema para o link verificado do
 * aplicativo. Ver ADR-020.
 */
sealed interface OAuthCallback {
    /** Código de uso único a trocar por uma sessão. */
    data class Code(val code: String) : OAuthCallback

    /** O provider autenticou, mas a conta exige o segundo fator. */
    data class MfaRequired(val challengeId: String) : OAuthCallback

    data object Failed : OAuthCallback

    companion object {
        /** Lê o App Link recebido, sem confiar em parâmetros ausentes. */
        fun fromCallbackUri(query: Map<String, String>): OAuthCallback? = when {
            query["error"] != null -> Failed
            query["mfaRequired"] == "true" ->
                query["challengeId"]?.takeIf(String::isNotBlank)?.let(::MfaRequired) ?: Failed
            query["code"]?.isNotBlank() == true -> Code(query.getValue("code"))
            else -> null
        }
    }
}
