package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.TotpCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {
}
