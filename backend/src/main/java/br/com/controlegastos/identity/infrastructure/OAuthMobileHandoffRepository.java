package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.OAuthMobileHandoff;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthMobileHandoffRepository extends JpaRepository<OAuthMobileHandoff, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select handoff from OAuthMobileHandoff handoff where handoff.codeHash = :hash")
    Optional<OAuthMobileHandoff> findLockedByCodeHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from OAuthMobileHandoff handoff where handoff.expiresAt < :cutoff "
            + "or (handoff.consumedAt is not null and handoff.consumedAt < :cutoff)")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
