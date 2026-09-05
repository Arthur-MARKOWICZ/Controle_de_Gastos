package br.com.controlegastos.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class TotpSecretCipherTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    private byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void encryptsAndDecryptsBackToTheOriginalSecret() {
        TotpSecretCipher cipher = new TotpSecretCipher(randomKey(), 1);

        TotpSecretCipher.EncryptedSecret encrypted = cipher.encrypt(SECRET);

        assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion())).isEqualTo(SECRET);
    }

    @Test
    void ciphertextNeverMatchesThePlainTextBytes() {
        TotpSecretCipher cipher = new TotpSecretCipher(randomKey(), 1);

        TotpSecretCipher.EncryptedSecret encrypted = cipher.encrypt(SECRET);

        assertThat(encrypted.ciphertext()).isNotEqualTo(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encryptingTheSameSecretTwiceProducesDifferentCiphertextAndNonce() {
        TotpSecretCipher cipher = new TotpSecretCipher(randomKey(), 1);

        TotpSecretCipher.EncryptedSecret first = cipher.encrypt(SECRET);
        TotpSecretCipher.EncryptedSecret second = cipher.encrypt(SECRET);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void tamperingWithTheCiphertextFailsToDecrypt() {
        TotpSecretCipher cipher = new TotpSecretCipher(randomKey(), 1);
        TotpSecretCipher.EncryptedSecret encrypted = cipher.encrypt(SECRET);
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[0] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(tampered, encrypted.nonce(), encrypted.keyVersion()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptingWithAMismatchedKeyVersionThrows() {
        TotpSecretCipher cipher = new TotpSecretCipher(randomKey(), 1);
        TotpSecretCipher.EncryptedSecret encrypted = cipher.encrypt(SECRET);

        assertThatThrownBy(() -> cipher.decrypt(encrypted.ciphertext(), encrypted.nonce(), 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAKeyThatIsNotThirtyTwoBytes() {
        assertThatThrownBy(() -> new TotpSecretCipher(new byte[16], 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
