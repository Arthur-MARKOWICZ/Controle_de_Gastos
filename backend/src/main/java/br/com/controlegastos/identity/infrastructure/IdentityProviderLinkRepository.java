package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.IdentityProviderLink;
import br.com.controlegastos.identity.domain.OAuthProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityProviderLinkRepository extends JpaRepository<IdentityProviderLink, UUID> {

    Optional<IdentityProviderLink> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    List<IdentityProviderLink> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    void deleteByUserIdAndProvider(UUID userId, OAuthProvider provider);
}
