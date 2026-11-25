package br.com.controlegastos.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.notifications.application.WebPushSender;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JdkWebPushSenderTest {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void encryptsPayloadSoOnlyTheBrowserCanReadIt() throws Exception {
        KeyPair browser = p256();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        JdkWebPushSender sender = sender(p256());

        byte[] body = sender.encrypt("{\"category\":\"DAILY_REMINDER\"}".getBytes(StandardCharsets.UTF_8),
                rawPublic((ECPublicKey) browser.getPublic()), auth);

        assertThat(new String(decrypt(body, browser, auth), StandardCharsets.UTF_8))
                .isEqualTo("{\"category\":\"DAILY_REMINDER\"}");
    }

    @Test
    void deliversWithVapidAuthorizationAndAes128gcmHeaders() throws Exception {
        KeyPair vapid = p256();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> encoding = new AtomicReference<>();
        AtomicReference<String> ttl = new AtomicReference<>();
        String endpoint = startServer(201, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            ttl.set(exchange.getRequestHeaders().getFirst("TTL"));
        });

        WebPushSender.Outcome outcome = sender(vapid).send(target(endpoint), "{}");

        assertThat(outcome).isEqualTo(WebPushSender.Outcome.DELIVERED);
        assertThat(encoding.get()).isEqualTo("aes128gcm");
        assertThat(ttl.get()).isEqualTo("86400");
        String header = authorization.get();
        assertThat(header).startsWith("vapid t=").contains(", k=" + B64URL.encodeToString(rawPublic((ECPublicKey) vapid.getPublic())));
        String jwt = header.substring("vapid t=".length(), header.indexOf(','));
        String[] parts = jwt.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(vapid.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(B64URL_DECODER.decode(parts[2]))).isTrue();
        JsonNode claims = new ObjectMapper().readTree(B64URL_DECODER.decode(parts[1]));
        assertThat(claims.get("aud").asString()).isEqualTo(endpoint.substring(0, endpoint.indexOf('/', 8)));
        assertThat(claims.get("sub").asString()).isEqualTo("mailto:privacidade@example.com");
        assertThat(claims.get("exp").asLong()).isGreaterThan(NOW.getEpochSecond());
    }

    @Test
    void reportsGoneFor404And410() throws Exception {
        assertThat(sender(p256()).send(target(startServer(410, exchange -> { })), "{}"))
                .isEqualTo(WebPushSender.Outcome.GONE);
        server.stop(0);
        assertThat(sender(p256()).send(target(startServer(404, exchange -> { })), "{}"))
                .isEqualTo(WebPushSender.Outcome.GONE);
    }

    @Test
    void reportsFailureForServerErrorsAndUnreachableEndpoints() throws Exception {
        assertThat(sender(p256()).send(target(startServer(503, exchange -> { })), "{}"))
                .isEqualTo(WebPushSender.Outcome.FAILED);
        assertThat(sender(p256()).send(target("http://127.0.0.1:1/push"), "{}"))
                .isEqualTo(WebPushSender.Outcome.FAILED);
    }

    @Test
    void isDisabledWithoutKeys() {
        JdkWebPushSender sender = new JdkWebPushSender("", "", "", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(sender.isEnabled()).isFalse();
        assertThat(sender.publicKey()).isNull();
    }

    private interface Inspector {
        void inspect(com.sun.net.httpserver.HttpExchange exchange);
    }

    private String startServer(int status, Inspector inspector) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            inspector.inspect(exchange);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc";
    }

    private WebPushSender.Target target(String endpoint) throws Exception {
        KeyPair browser = p256();
        byte[] auth = new byte[16];
        return new WebPushSender.Target(endpoint, B64URL.encodeToString(rawPublic((ECPublicKey) browser.getPublic())),
                B64URL.encodeToString(auth));
    }

    private JdkWebPushSender sender(KeyPair vapid) {
        byte[] d = ((ECPrivateKey) vapid.getPrivate()).getS().toByteArray();
        byte[] d32 = new byte[32];
        System.arraycopy(d, Math.max(0, d.length - 32), d32, Math.max(0, 32 - d.length), Math.min(32, d.length));
        return new JdkWebPushSender(B64URL.encodeToString(rawPublic((ECPublicKey) vapid.getPublic())),
                B64URL.encodeToString(d32), "mailto:privacidade@example.com", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] rawPublic(ECPublicKey key) {
        return JdkWebPushSender.rawPublicKey(key);
    }

    /** Decifra como o navegador faria (RFC 8291 + RFC 8188), para conferir a cifragem. */
    private static byte[] decrypt(byte[] body, KeyPair browser, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] serverPublic = new byte[buffer.get() & 0xff];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyFactory factory = KeyFactory.getInstance("EC");
        ECPublicKey browserPublic = (ECPublicKey) browser.getPublic();
        ECPublicKey asPublic = JdkWebPushSender.publicKeyFrom(serverPublic, browserPublic.getParams());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(asPublic, true);
        byte[] ecdh = agreement.generateSecret();
        assertThat(factory).isNotNull();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), rawPublic(browserPublic), serverPublic);
        byte[] ikm = hkdf(auth, ecdh, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
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
}
