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
    val data = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
    if (data.path != "/app/oauth/callback") return null
    val query = data.queryParameterNames.associateWith { data.getQueryParameter(it).orEmpty() }
    return OAuthCallback.fromCallbackUri(query)
}
