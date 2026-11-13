package br.com.controlegastos.identity.application;

import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("dois tokens da mesma sessão têm segredos e hashes diferentes")
    void tokensDaMesmaSessaoDiferem() {
        UUID sessionId = UUID.randomUUID();

        assertThat(codec.issue(sessionId).hash()).isNotEqualTo(codec.issue(sessionId).hash());
    }

    @Test
    @DisplayName("o hash guardado não é o segredo bruto")
    void hashNaoESegredo() {
        var issued = codec.issue(UUID.randomUUID());

        assertThat(issued.rawValue()).doesNotContain(issued.hash());
        assertThat(issued.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("token nulo lança InvalidRefreshTokenException")
    void tokenNuloFalha() {
        assertThatThrownBy(() -> codec.parse(null)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("token sem separador lança InvalidRefreshTokenException")
    void semSeparadorFalha() {
        assertThatThrownBy(() -> codec.parse(UUID.randomUUID().toString())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("token com sessão que não é UUID lança InvalidRefreshTokenException")
    void sessaoInvalidaFalha() {
        assertThatThrownBy(() -> codec.parse("nao-uuid." + "a".repeat(43))).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("segredo com caractere fora do base64url lança InvalidRefreshTokenException")
    void segredoForaDoAlfabetoFalha() {
        assertThatThrownBy(() -> codec.parse(UUID.randomUUID() + "." + "a".repeat(42) + "+"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("token terminado no separador lança InvalidRefreshTokenException")
    void separadorNoFimFalha() {
        assertThatThrownBy(() -> codec.parse(UUID.randomUUID() + ".")).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
