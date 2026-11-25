package br.com.controlegastos.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Par VAPID gerado na hora para testes; nunca é uma chave real. */
public record TestVapidKeys(String publicKey, String privateKey) {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    public static TestVapidKeys generate() {
        KeyPair pair = p256();
        return new TestVapidKeys(B64URL.encodeToString(raw((ECPublicKey) pair.getPublic())),
                B64URL.encodeToString(fixed32(((ECPrivateKey) pair.getPrivate()).getS().toByteArray())));
    }

    /** Chave {@code p256dh} válida de um "navegador" de teste. */
    public static String browserKey() {
        return B64URL.encodeToString(raw((ECPublicKey) p256().getPublic()));
    }

    /** Segredo {@code auth} de 16 bytes. */
    public static String authSecret() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        return B64URL.encodeToString(secret);
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("app.notifications.vapid.public-key", this::publicKey);
        registry.add("app.notifications.vapid.private-key", this::privateKey);
        registry.add("app.notifications.vapid.subject", () -> "mailto:privacidade@example.com");
    }

    private static KeyPair p256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] raw(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed32(key.getW().getAffineX().toByteArray()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY().toByteArray()), 0, out, 33, 32);
        return out;
    }

    private static byte[] fixed32(byte[] bytes) {
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }
}
