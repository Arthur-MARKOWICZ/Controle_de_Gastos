package br.com.controlegastos.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TotpSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    public TotpSecretCipher(byte[] keyBytes, int keyVersion) {
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AUTH_TOTP_ENCRYPTION_KEY deve ter 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.keyVersion = keyVersion;
    }

    public EncryptedSecret encrypt(String base32Secret) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(base32Secret.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(ciphertext, nonce, keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao cifrar segredo TOTP", exception);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] nonce, int storedKeyVersion) {
        if (storedKeyVersion != keyVersion) {
            throw new IllegalStateException("Versão de chave de cifragem TOTP não corresponde à chave configurada");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao decifrar segredo TOTP", exception);
        }
    }

    public record EncryptedSecret(byte[] ciphertext, byte[] nonce, int keyVersion) {
    }
}
