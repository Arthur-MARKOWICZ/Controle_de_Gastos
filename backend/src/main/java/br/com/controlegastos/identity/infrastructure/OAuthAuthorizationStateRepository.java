package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.OAuthAuthorizationState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthAuthorizationStateRepository extends JpaRepository<OAuthAuthorizationState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from OAuthAuthorizationState state where state.stateHash = :hash")
    Optional<OAuthAuthorizationState> findLockedByStateHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from OAuthAuthorizationState state where state.expiresAt < :cutoff "
            + "or (state.consumedAt is not null and state.consumedAt < :cutoff)")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
