package br.com.controlegastos.app

/** Ação possível por provedor na tela de segurança. */
data class ProviderLoginAction(
    val provider: OAuthProvider,
    val linked: Boolean,
    val canUnlink: Boolean,
    val canConnect: Boolean,
)

/**
 * Deriva botões de conectar/desconectar a partir de [LoginMethods].
 * Conectar só aparece quando o provedor ainda não está vinculado; desconectar
 * exige que não seja o último método (mesma regra da API).
 */
fun providerLoginActions(methods: LoginMethods): List<ProviderLoginAction> =
    OAuthProvider.entries.map { provider ->
        val linked = provider in methods.linkedProviders
        ProviderLoginAction(
            provider = provider,
            linked = linked,
            canUnlink = linked && !methods.isLastMethod,
            canConnect = !linked,
        )
    }
