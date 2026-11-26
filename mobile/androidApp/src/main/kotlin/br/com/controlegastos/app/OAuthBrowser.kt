package br.com.controlegastos.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Abre o login social no navegador do sistema.
 *
 * A RFC 8252 desaconselha WebView embutida: ela daria ao aplicativo acesso às
 * credenciais digitadas no provedor e não reaproveita a sessão do navegador.
 */
fun Context.openOAuthInBrowser(url: String) {
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .also { it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(this, uri)
    }.onFailure {
        // Sem navegador compatível com Custom Tabs, o navegador padrão serve.
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Lê o App Link de retorno do login social, se for esse o intent recebido. */
fun oauthCallbackOf(intent: Intent?): OAuthCallback? {
    val data = intent?.data ?: return null
    return oauthCallbackOf(
        action = intent.action,
        path = data.path,
        query = { data.queryParameterNames.associateWith { data.getQueryParameter(it).orEmpty() } },
    )
}

/** Regra do App Link sem tipos do Android: só `ACTION_VIEW` no caminho de retorno do login social. */
internal fun oauthCallbackOf(action: String?, path: String?, query: () -> Map<String, String>): OAuthCallback? {
    if (action != Intent.ACTION_VIEW || path != OAUTH_CALLBACK_PATH) return null
    return OAuthCallback.fromCallbackUri(query())
}

internal const val OAUTH_CALLBACK_PATH = "/app/oauth/callback"
