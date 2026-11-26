package br.com.controlegastos.app

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Onde o cookie de refresh fica entre aberturas do app. */
interface RefreshTokenStore {
    fun save(cookie: String)
    fun load(): String?
    fun clear()
}

/**
 * Cifra o cookie de refresh com AES-GCM. A chave vem de fora — o Android Keystore
 * no aparelho — para a regra de cifragem poder ser verificada na JVM.
 */
internal class RefreshCookieCipher(private val key: () -> SecretKey) {

    class Sealed(val cipherText: ByteArray, val iv: ByteArray)

    fun seal(cookie: String): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Sealed(cipher.doFinal(cookie.toByteArray(Charsets.UTF_8)), cipher.iv)
    }

    /** Lança [java.security.GeneralSecurityException] se o conteúdo foi alterado ou a chave mudou. */
    fun open(sealed: Sealed): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed.iv))
        return cipher.doFinal(sealed.cipherText).toString(Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
