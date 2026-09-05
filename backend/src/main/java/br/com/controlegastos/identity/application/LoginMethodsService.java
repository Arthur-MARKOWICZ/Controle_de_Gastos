package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.EmailAddress;
import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthProvider;
import br.com.controlegastos.identity.domain.UserAccount;
import br.com.controlegastos.identity.infrastructure.IdentityProviderLinkRepository;
import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginMethodsService {

    private final UserAccountRepository users;
    private final IdentityProviderLinkRepository links;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public LoginMethodsService(
            UserAccountRepository users,
            IdentityProviderLinkRepository links,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.users = users;
        this.links = links;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LoginMethods status(UUID userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
        List<OAuthProvider> providers = links.findByUserId(userId).stream().map(IdentityProviderLink::provider).toList();
        return new LoginMethods(user.hasPassword(), providers);
    }

    @Transactional
    public void addPassword(UUID userId, String password) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
        if (user.hasPassword()) {
            throw new PasswordAlreadySetException();
        }
        AuthenticationService.validatePassword(new EmailAddress(user.emailNormalized()), password);
        user.attachPassword(passwordEncoder.encode(password), clock.instant());
    }

    @Transactional
    public void unlink(UUID userId, OAuthProvider provider) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
        List<IdentityProviderLink> existing = links.findByUserId(userId);
        boolean isLinked = existing.stream().anyMatch(link -> link.provider() == provider);
        if (!isLinked) {
            return;
        }
        long remainingProviders = existing.stream().filter(link -> link.provider() != provider).count();
        if (!user.hasPassword() && remainingProviders == 0) {
            throw new LastLoginMethodException();
        }
        links.deleteByUserIdAndProvider(userId, provider);
    }

    public record LoginMethods(boolean hasPassword, List<OAuthProvider> linkedProviders) {
    }
}
