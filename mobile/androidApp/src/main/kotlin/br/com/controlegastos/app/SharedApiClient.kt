package br.com.controlegastos.app

import android.content.Context

/**
 * Um único [AndroidApiClient] por processo. A tela e o worker do lembrete precisam
 * compartilhar o mesmo cliente: o refresh é rotativo, e duas renovações paralelas
 * com o mesmo token disparariam a detecção de reuso e encerrariam a sessão.
 */
object SharedApiClient {
    @Volatile
    private var instance: AndroidApiClient? = null

    fun get(context: Context): AndroidApiClient = instance ?: synchronized(this) {
        instance ?: AndroidApiClient(context.applicationContext, BuildConfig.API_BASE_URL).also { instance = it }
    }
}
