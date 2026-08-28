package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.AuthAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, String> {

    @Query(value = "SELECT hashtext(:attemptKey) FROM "
            + "(SELECT pg_advisory_xact_lock(hashtext(:attemptKey))) AS acquired", nativeQuery = true)
    Integer lockKey(@Param("attemptKey") String attemptKey);

    @Modifying
    @Query("delete from AuthAttempt attempt where attempt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
