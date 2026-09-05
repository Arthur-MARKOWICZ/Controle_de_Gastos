package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.TotpCredential;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {

    @Modifying
    @Query("update TotpCredential credential set "
            + "credential.status = br.com.controlegastos.identity.domain.TotpCredentialStatus.DISABLED, "
            + "credential.secretCiphertext = null, credential.secretNonce = null, credential.keyVersion = null, "
            + "credential.pendingExpiresAt = null, credential.updatedAt = :now "
            + "where credential.status = br.com.controlegastos.identity.domain.TotpCredentialStatus.PENDING "
            + "and credential.pendingExpiresAt < :now")
    int discardAbandonedPending(@Param("now") Instant now);
}
