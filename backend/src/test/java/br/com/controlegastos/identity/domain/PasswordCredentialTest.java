package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordCredentialTest {

    private static final Instant NOW = Instant.parse("2026-09-15T15:00:00Z");

    @Test
    @DisplayName("guarda o hash e a data da troca de senha")
    void guardaHashEData() {
        PasswordCredential credential = new PasswordCredential("$argon2id$hash", NOW);

        assertThat(credential.passwordHash()).isEqualTo("$argon2id$hash");
        assertThat(credential.passwordChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("recusa hash ausente com mensagem explícita")
    void recusaHashAusente() {
        assertThatThrownBy(() -> new PasswordCredential(null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Hash da senha é obrigatório");
    }

    @Test
    @DisplayName("recusa data de troca ausente com mensagem explícita")
    void recusaDataAusente() {
        assertThatThrownBy(() -> new PasswordCredential("$argon2id$hash", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Data de troca da senha é obrigatória");
    }

    @Test
    @DisplayName("trocar a senha da conta substitui a credencial anterior")
    void trocarSenhaSubstituiCredencial() {
        UserAccount user = UserAccount.register(EmailAddress.from("credencial@example.com"), "hash-antigo", NOW);

        user.changePasswordAndVerifyEmail("hash-novo", NOW.plusSeconds(60));

        assertThat(user.passwordCredential().passwordHash()).isEqualTo("hash-novo");
        assertThat(user.passwordCredential().passwordChangedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("conta criada por provedor não tem credencial de senha")
    void contaDeProvedorNaoTemSenha() {
        UserAccount user = UserAccount.registerWithProvider(EmailAddress.from("social@example.com"), NOW);

        assertThat(user.hasPassword()).isFalse();
        assertThat(user.passwordCredential()).isNull();
    }

    @Test
    @DisplayName("anexar senha a conta que já tem senha lança IllegalStateException")
    void anexarSenhaDuplicadaFalha() {
        UserAccount user = UserAccount.register(EmailAddress.from("dupla@example.com"), "hash", NOW);

        assertThatThrownBy(() -> user.attachPassword("outro-hash", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A conta já tem uma senha definida");
    }

    @Test
    @DisplayName("anexar senha a conta social cria a credencial")
    void anexarSenhaAContaSocial() {
        UserAccount user = UserAccount.registerWithProvider(EmailAddress.from("anexa@example.com"), NOW);

        user.attachPassword("hash", NOW);

        assertThat(user.hasPassword()).isTrue();
        assertThat(user.passwordCredential().passwordHash()).isEqualTo("hash");
    }
}
