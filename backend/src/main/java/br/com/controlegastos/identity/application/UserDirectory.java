package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de identidade para outros módulos (ex.: convite de compartilhamento).
 * Não expõe senha nem tokens.
 */
@Service
@NamedInterface("directory")
public class UserDirectory {

    private final UserAccountRepository users;

    public UserDirectory(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<VerifiedUser> findVerifiedByEmail(String rawEmail) {
        EmailAddress email;
        try {
            email = EmailAddress.from(rawEmail);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return users.findByEmailNormalized(email.value())
                .filter(user -> user.emailVerifiedAt() != null)
                .map(user -> new VerifiedUser(user.id(), user.emailNormalized()));
    }

    @Transactional(readOnly = true)
    public Optional<String> emailOf(UUID userId) {
        return users.findById(userId).map(UserAccount::emailNormalized);
    }

    public record VerifiedUser(UUID id, String email) {
    }
}
