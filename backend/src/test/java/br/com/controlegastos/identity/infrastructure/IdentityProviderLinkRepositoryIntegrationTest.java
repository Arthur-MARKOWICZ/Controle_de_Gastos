package br.com.controlegastos.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.domain.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class IdentityProviderLinkRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserAccountRepository users;

    @Autowired
    IdentityProviderLinkRepository links;

    @Test
    void rejectsTheSameProviderAccountLinkedToTwoDifferentUsers() {
        Instant now = Instant.now();
        UserAccount first = UserAccount.registerWithProvider(EmailAddress.from("primeira@example.com"), now);
        UserAccount second = UserAccount.registerWithProvider(EmailAddress.from("segunda@example.com"), now);
        users.save(first);
        users.save(second);
        links.saveAndFlush(
                IdentityProviderLink.link(first.id(), OAuthProvider.GOOGLE, "google-123", "primeira@example.com", now));

        assertThatThrownBy(() -> links.saveAndFlush(
                IdentityProviderLink.link(second.id(), OAuthProvider.GOOGLE, "google-123", "segunda@example.com", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTheSameProviderLinkedTwiceToTheSameUser() {
        Instant now = Instant.now();
        UserAccount user = UserAccount.registerWithProvider(EmailAddress.from("terceira@example.com"), now);
        users.save(user);
        links.saveAndFlush(
                IdentityProviderLink.link(user.id(), OAuthProvider.GOOGLE, "google-1", "terceira@example.com", now));

        assertThatThrownBy(() -> links.saveAndFlush(
                IdentityProviderLink.link(user.id(), OAuthProvider.GOOGLE, "google-2", "terceira@example.com", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameUserToLinkTwoDifferentProviders() {
        Instant now = Instant.now();
        UserAccount user = UserAccount.registerWithProvider(EmailAddress.from("quarta@example.com"), now);
        users.save(user);
        links.saveAndFlush(
                IdentityProviderLink.link(user.id(), OAuthProvider.GOOGLE, "google-9", "quarta@example.com", now));
        links.saveAndFlush(
                IdentityProviderLink.link(user.id(), OAuthProvider.GITHUB, "github-9", "quarta@example.com", now));

        assertThat(links.countByUserId(user.id())).isEqualTo(2);
    }
}
