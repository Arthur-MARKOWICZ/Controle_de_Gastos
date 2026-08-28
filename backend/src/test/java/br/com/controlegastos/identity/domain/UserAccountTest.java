package br.com.controlegastos.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
    }
}
