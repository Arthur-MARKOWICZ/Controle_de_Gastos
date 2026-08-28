package br.com.controlegastos.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCodec {

    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedRefreshToken issue(UUID sessionId) {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedRefreshToken(sessionId + "." + encodedSecret, hash(encodedSecret));
    }

    public ParsedRefreshToken parse(String rawToken) {
        if (rawToken == null) {
            throw new InvalidRefreshTokenException();
        }
        int separator = rawToken.indexOf('.');
        if (separator <= 0 || separator == rawToken.length() - 1) {
            throw new InvalidRefreshTokenException();
        }
        try {
            UUID sessionId = UUID.fromString(rawToken.substring(0, separator));
            String secret = rawToken.substring(separator + 1);
            if (secret.length() != 43 || !secret.matches("[A-Za-z0-9_-]{43}")) {
                throw new InvalidRefreshTokenException();
            }
            return new ParsedRefreshToken(sessionId, hash(secret));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    public record IssuedRefreshToken(String rawValue, String hash) {
    }

    public record ParsedRefreshToken(UUID sessionId, String hash) {
    }
}
