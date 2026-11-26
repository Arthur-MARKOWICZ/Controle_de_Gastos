package br.com.controlegastos.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class EncryptedRefreshTokenStore(context: Context) : RefreshTokenStore {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
    private val cipher = RefreshCookieCipher(::key)

    @Synchronized
    override fun save(cookie: String) {
        val sealed = cipher.seal(cookie)
        preferences.edit()
            .putString(CIPHER_TEXT, Base64.encodeToString(sealed.cipherText, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(sealed.iv, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    override fun load(): String? {
        val encrypted = preferences.getString(CIPHER_TEXT, null) ?: return null
        val iv = preferences.getString(IV, null) ?: return null
        return runCatching {
            cipher.open(RefreshCookieCipher.Sealed(Base64.decode(encrypted, Base64.NO_WRAP), Base64.decode(iv, Base64.NO_WRAP)))
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "controle_gastos_refresh_v1"
        const val CIPHER_TEXT = "refresh_ciphertext"
        const val IV = "refresh_iv"
    }
}
