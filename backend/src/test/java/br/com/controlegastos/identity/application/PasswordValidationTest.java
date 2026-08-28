package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.identity.domain.EmailAddress;
import org.junit.jupiter.api.Test;

class PasswordValidationTest {

    @Test
    void acceptsLongPassphrasesWithSpacesAndUnicode() {
        assertThatCode(() -> AuthenticationService.validatePassword(
                EmailAddress.from("pessoa@example.com"),
                "minha frase segura 🔒"
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsShorterThanTwelveCharacters() {
        assertThatThrownBy(() -> AuthenticationService.validatePassword(
                EmailAddress.from("pessoa@example.com"),
                "curta123"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPasswordEqualToEmailIgnoringCase() {
        assertThatThrownBy(() -> AuthenticationService.validatePassword(
                EmailAddress.from("pessoa@example.com"),
                "PESSOA@EXAMPLE.COM"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPasswordsLongerThanOneHundredTwentyEightCharacters() {
        assertThatThrownBy(() -> AuthenticationService.validatePassword(
                EmailAddress.from("pessoa@example.com"),
                "a".repeat(129)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
