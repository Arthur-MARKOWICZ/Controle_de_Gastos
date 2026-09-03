package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.AuthSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    <S extends AuthSession> S save(S session);
    Optional<AuthSession> findById(UUID id);
    Optional<AuthSession> findLockedById(UUID id);
    int revokeActiveByUserId(UUID userId, Instant now, String reason);
    int deleteEndedBefore(Instant cutoff);
}
