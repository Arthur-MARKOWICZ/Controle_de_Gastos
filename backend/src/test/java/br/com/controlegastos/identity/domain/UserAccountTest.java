package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    @Test
    void registrationKeepsThePasswordCredentialWithTheUserAggregate() {
        Instant registeredAt = Instant.parse("2026-08-28T12:00:00Z");

        UserAccount account = UserAccount.register(
                EmailAddress.from("pessoa@example.com"),
                "$argon2id$encoded-password",
                registeredAt
        );

        assertThat(account.passwordCredential().passwordHash()).isEqualTo("$argon2id$encoded-password");
        assertThat(account.passwordCredential().passwordChangedAt()).isEqualTo(registeredAt);
        assertThat(account.hasPassword()).isTrue();
    }

    @Test
    void registrationViaProviderHasNoPassword() {
        Instant registeredAt = Instant.parse("2026-09-05T12:00:00Z");

        UserAccount account = UserAccount.registerWithProvider(
                EmailAddress.from("pessoa@example.com"),
                registeredAt
        );

        assertThat(account.hasPassword()).isFalse();
        assertThat(account.passwordCredential()).isNull();
    }

    @Test
    void attachingAPasswordToAProviderOnlyAccountMakesItAvailable() {
        Instant registeredAt = Instant.parse("2026-09-05T12:00:00Z");
        Instant attachedAt = registeredAt.plusSeconds(60);
        UserAccount account = UserAccount.registerWithProvider(EmailAddress.from("pessoa@example.com"), registeredAt);

        account.attachPassword("$argon2id$hash", attachedAt);

        assertThat(account.hasPassword()).isTrue();
        assertThat(account.passwordCredential().passwordHash()).isEqualTo("$argon2id$hash");
        assertThat(account.passwordCredential().passwordChangedAt()).isEqualTo(attachedAt);
    }

    @Test
    void cannotAttachAPasswordWhenTheAccountAlreadyHasOne() {
        Instant registeredAt = Instant.parse("2026-09-05T12:00:00Z");
        UserAccount account = UserAccount.register(EmailAddress.from("pessoa@example.com"), "hash", registeredAt);

        assertThatThrownBy(() -> account.attachPassword("outro-hash", registeredAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
