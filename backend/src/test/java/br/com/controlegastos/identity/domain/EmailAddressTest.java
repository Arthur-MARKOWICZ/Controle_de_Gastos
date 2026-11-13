package br.com.controlegastos.identity.domain;

import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void normalizesWhitespaceAndCase() {
        assertThat(EmailAddress.from("  Pessoa.Exemplo@Example.COM  ").value())
                .isEqualTo("pessoa.exemplo@example.com");
    }

    @Test
    void rejectsMalformedAddresses() {
        assertThatThrownBy(() -> EmailAddress.from("email-invalido"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("e-mail nulo lança IllegalArgumentException com mensagem")
    void emailNuloFalha() {
        assertThatThrownBy(() -> EmailAddress.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail é obrigatório");
    }

    @Test
    @DisplayName("e-mail sem domínio com ponto é inválido")
    void semPontoNoDominio() {
        assertThatThrownBy(() -> EmailAddress.from("pessoa@localhost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail inválido");
    }

    @Test
    @DisplayName("e-mail acima de 254 caracteres é inválido")
    void emailLongoDemais() {
        String local = "a".repeat(64);
        String domain = "b".repeat(186) + ".com";
        assertThatThrownBy(() -> EmailAddress.from(local + "@" + domain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail inválido");
    }

    @Test
    @DisplayName("e-mail com espaço interno é inválido")
    void espacoInterno() {
        assertThatThrownBy(() -> EmailAddress.from("pes soa@example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("e-mail com dois arrobas é inválido")
    void doisArrobas() {
        assertThatThrownBy(() -> EmailAddress.from("a@b@example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("e-mail com exatamente 254 caracteres é aceito")
    void limiteDe254Aceito() {
        String value = "a".repeat(64) + "@" + "b".repeat(185) + ".com";
        assertThat(value).hasSize(254);
        assertThat(EmailAddress.from(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("construtor direto não normaliza: maiúsculas continuam distintas")
    void construtorDiretoNaoNormaliza() {
        assertThat(new EmailAddress("Pessoa@Example.com")).isNotEqualTo(EmailAddress.from("Pessoa@Example.com"));
    }
}
