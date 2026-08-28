package br.com.controlegastos.identity.domain;

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
}
