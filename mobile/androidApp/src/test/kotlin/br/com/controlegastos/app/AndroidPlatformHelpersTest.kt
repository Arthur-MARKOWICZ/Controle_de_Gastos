package br.com.controlegastos.app

import android.content.Intent
import android.content.SharedPreferences
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cofre do refresh, tema salvo, App Link do OAuth e tipo do relatório. */
class AndroidPlatformHelpersTest {

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    // --------------------------------------------------------- cifra do refresh

    @Test
    fun `cookie cifrado volta igual com a mesma chave`() {
        val cipher = RefreshCookieCipher(aesKey().let { key -> { key } })

        assertEquals("refresh_token=abc", cipher.open(cipher.seal("refresh_token=abc")))
    }

    @Test
    fun `texto cifrado nao contem o cookie e muda a cada gravacao`() {
        val cipher = RefreshCookieCipher(aesKey().let { key -> { key } })

        val first = cipher.seal("refresh_token=abc")
        val second = cipher.seal("refresh_token=abc")

        assertFalse(String(first.cipherText, Charsets.ISO_8859_1).contains("refresh_token"))
        assertFalse(first.iv.contentEquals(second.iv))
    }

    @Test
    fun `conteudo adulterado e recusado`() {
        val cipher = RefreshCookieCipher(aesKey().let { key -> { key } })
        val sealed = cipher.seal("refresh_token=abc")
        sealed.cipherText[0] = (sealed.cipherText[0].toInt() xor 1).toByte()

        assertFailsWith<AEADBadTagException> { cipher.open(sealed) }
    }

    @Test
    fun `outra chave nao abre o cookie`() {
        val sealed = RefreshCookieCipher(aesKey().let { key -> { key } }).seal("refresh_token=abc")

        assertFailsWith<GeneralSecurityException> { RefreshCookieCipher(aesKey().let { key -> { key } }).open(sealed) }
    }

    // ------------------------------------------------------------------- tema

    @Test
    fun `tema salvo volta na proxima leitura`() {
        val store = AndroidThemePreferenceStore(FakePreferences())

        store.write(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, store.read())
    }

    @Test
    fun `sem escolha salva segue o sistema`() {
        assertEquals(ThemeMode.SYSTEM, AndroidThemePreferenceStore(FakePreferences()).read())
    }

    @Test
    fun `valor desconhecido salvo por versao antiga volta para o sistema`() {
        val preferences = FakePreferences().apply { values["theme_mode"] = "SEPIA" }

        assertEquals(ThemeMode.SYSTEM, AndroidThemePreferenceStore(preferences).read())
    }

    // ------------------------------------------------------------- App Link

    @Test
    fun `retorno do login social com codigo vira Code`() {
        assertEquals(
            OAuthCallback.Code("abc"),
            oauthCallbackOf(Intent.ACTION_VIEW, OAUTH_CALLBACK_PATH) { mapOf("code" to "abc") },
        )
    }

    @Test
    fun `link de outro caminho ou outra acao e ignorado`() {
        assertNull(oauthCallbackOf(Intent.ACTION_VIEW, "/app/outra-coisa") { mapOf("code" to "abc") })
        assertNull(oauthCallbackOf("android.intent.action.MAIN", OAUTH_CALLBACK_PATH) { mapOf("code" to "abc") })
        assertNull(oauthCallbackOf(null, null) { error("não deveria ler a query") })
    }

    @Test
    fun `erro do provedor ou MFA sem desafio viram falha`() {
        assertEquals(OAuthCallback.Failed, oauthCallbackOf(Intent.ACTION_VIEW, OAUTH_CALLBACK_PATH) { mapOf("error" to "oauth_failed") })
        assertEquals(OAuthCallback.Failed, oauthCallbackOf(Intent.ACTION_VIEW, OAUTH_CALLBACK_PATH) { mapOf("mfaRequired" to "true") })
    }

    // ------------------------------------------------------------- relatório

    @Test
    fun `tipo do arquivo segue a extensao sem diferenciar maiusculas`() {
        assertEquals("text/csv", mimeTypeOf("gastos.CSV"))
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", mimeTypeOf("gastos.xlsx"))
    }

    @Test
    fun `extensao desconhecida ou disfarcada vira binario generico`() {
        assertEquals("application/octet-stream", mimeTypeOf("gastos.csv.exe"))
        assertEquals("application/octet-stream", mimeTypeOf("gastos"))
        assertTrue(mimeTypeOf("").isNotEmpty())
    }

    private class FakePreferences : SharedPreferences {
        val values = mutableMapOf<String, String?>()
        override fun getString(key: String?, defValue: String?): String? = if (values.containsKey(key)) values[key] else defValue
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { values[key!!] = value }
            override fun apply() = Unit
            override fun commit() = true
            override fun clear() = apply { values.clear() }
            override fun remove(key: String?) = apply { values.remove(key) }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putLong(key: String?, value: Long) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putBoolean(key: String?, value: Boolean) = this
        }
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
