package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenCodecTest {
    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void issuesExactlyTwoHundredFiftySixBitsAndParsesTheSession() {
        UUID sessionId = UUID.randomUUID();

        var issued = codec.issue(sessionId);
        String secret = issued.rawValue().substring(issued.rawValue().indexOf('.') + 1);

        assertThat(Base64.getUrlDecoder().decode(secret)).hasSize(32);
        assertThat(codec.parse(issued.rawValue()).sessionId()).isEqualTo(sessionId);
        assertThat(codec.parse(issued.rawValue()).hash()).isEqualTo(issued.hash());
    }

    @Test
    void rejectsSecretsOutsideTheIssuedFormatBeforeHashing() {
        String oversized = UUID.randomUUID() + "." + "a".repeat(4_000);

        assertThatThrownBy(() -> codec.parse(oversized))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
