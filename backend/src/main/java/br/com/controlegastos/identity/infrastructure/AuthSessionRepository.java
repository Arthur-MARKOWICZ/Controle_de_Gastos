package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.application.SessionRepository;
import br.com.controlegastos.identity.domain.AuthSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID>, SessionRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findLockedById(@Param("id") UUID id);

    @Override
    @Modifying
    @Query("delete from AuthSession session where "
            + "(session.revokedAt is not null and session.revokedAt < :cutoff) or "
            + "(session.revokedAt is null and session.idleExpiresAt < :cutoff)")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
