package br.com.controlegastos.notifications.infrastructure;

import br.com.controlegastos.notifications.application.WebPushSender;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web Push com a JCA do JDK: VAPID (RFC 8292, JWT ES256) e cifragem {@code aes128gcm}
 * (RFC 8291/8188). Sem chaves configuradas, fica desligado (ADR-0033).
 * Nunca registra endpoint, chaves nem payload em log.
 */
@Component
class JdkWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(JdkWebPushSender.class);
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;
    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration JWT_LIFETIME = Duration.ofHours(12);
    private static final ECParameterSpec P256 = p256();

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final SecureRandom random = new SecureRandom();

    JdkWebPushSender(@Value("${app.notifications.vapid.public-key:}") String publicKey,
                     @Value("${app.notifications.vapid.private-key:}") String privateKey,
                     @Value("${app.notifications.vapid.subject:}") String subject,
                     Clock clock) {
        this.clock = clock;
        this.subject = subject;
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            this.publicKey = null;
            this.privateKey = null;
            log.warn("web_push_disabled reason=missing_vapid_keys");
            return;
        }
        this.publicKey = publicKey.trim();
        this.privateKey = privateKeyFrom(B64URL_DECODER.decode(privateKey.trim()));
        publicKeyFrom(B64URL_DECODER.decode(this.publicKey), P256);
    }

    @Override
    public boolean isEnabled() {
        return privateKey != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public Outcome send(Target target, String payloadJson) {
        if (!isEnabled()) {
            return Outcome.FAILED;
        }
        try {
            URI endpoint = URI.create(target.endpoint());
            byte[] body = encrypt(payloadJson.getBytes(StandardCharsets.UTF_8),
                    B64URL_DECODER.decode(target.p256dh()), B64URL_DECODER.decode(target.auth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "vapid t=" + jwt(endpoint) + ", k=" + publicKey)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", Long.toString(TTL.toSeconds()))
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return Outcome.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            log.warn("web_push_rejected status={}", status);
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("web_push_failed error={}", e.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    /** Corpo {@code aes128gcm} de um único registro, conforme RFC 8291. */
    byte[] encrypt(byte[] payload, byte[] browserPublicRaw, byte[] authSecret) throws GeneralSecurityException {
        ECPublicKey browserPublic = publicKeyFrom(browserPublicRaw, P256);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), random);
        KeyPair ephemeral = generator.generateKeyPair();
        byte[] serverPublicRaw = rawPublicKey((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(browserPublic, true);
        byte[] sharedSecret = agreement.generateSecret();

        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublicRaw, serverPublicRaw);
        byte[] ikm = hkdf(authSecret, sharedSecret, keyInfo, 32);
        byte[] contentKey = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(payload, new byte[] {2}));

        return ByteBuffer.allocate(16 + 4 + 1 + serverPublicRaw.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) serverPublicRaw.length)
                .put(serverPublicRaw)
                .put(ciphertext)
                .array();
    }

    private String jwt(URI endpoint) throws GeneralSecurityException {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", endpoint.getScheme() + "://" + endpoint.getRawAuthority());
        claims.put("exp", clock.instant().plus(JWT_LIFETIME).getEpochSecond());
        claims.put("sub", subject);
        String unsigned = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + B64URL.encodeToString(json.writeValueAsBytes(claims));
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey);
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + B64URL.encodeToString(signature.sign());
    }

    static byte[] rawPublicKey(ECPublicKey key) {
        return concat(new byte[] {4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    static ECPublicKey publicKeyFrom(byte[] raw, ECParameterSpec params) {
        if (raw.length != 65 || raw[0] != 4) {
            throw new IllegalArgumentException("Chave pública P-256 inválida");
        }
        try {
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, params));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Chave pública P-256 inválida", e);
        }
    }

    private static ECPrivateKey privateKeyFrom(byte[] d) {
        if (d.length != 32) {
            throw new IllegalArgumentException("Chave privada VAPID deve ter 32 bytes");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Chave privada VAPID inválida", e);
        }
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponível no JDK", e);
        }
    }
}
