package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :hash")
    Optional<PasswordResetToken> findLockedByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("update PasswordResetToken token set token.invalidatedAt = :now "
            + "where token.userId = :userId and token.consumedAt is null "
            + "and token.invalidatedAt is null and token.expiresAt > :now")
    int invalidateActiveForUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken token where token.expiresAt < :cutoff "
            + "or (token.consumedAt is not null and token.consumedAt < :cutoff) "
            + "or (token.invalidatedAt is not null and token.invalidatedAt < :cutoff)")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
