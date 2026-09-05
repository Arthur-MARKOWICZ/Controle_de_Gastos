package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.RecoveryCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    @Modifying
    @Query("update RecoveryCode code set code.invalidatedAt = :now "
            + "where code.userId = :userId and code.consumedAt is null and code.invalidatedAt is null")
    int invalidateActiveForUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RecoveryCode code where "
            + "(code.consumedAt is not null and code.consumedAt < :cutoff) "
            + "or (code.invalidatedAt is not null and code.invalidatedAt < :cutoff)")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
