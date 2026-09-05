package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.MfaLoginChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaLoginChallengeRepository extends JpaRepository<MfaLoginChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from MfaLoginChallenge challenge where challenge.challengeHash = :hash")
    Optional<MfaLoginChallenge> findLockedByChallengeHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from MfaLoginChallenge challenge where challenge.expiresAt < :cutoff "
            + "or (challenge.consumedAt is not null and challenge.consumedAt < :cutoff) "
            + "or (challenge.invalidatedAt is not null and challenge.invalidatedAt < :cutoff)")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
