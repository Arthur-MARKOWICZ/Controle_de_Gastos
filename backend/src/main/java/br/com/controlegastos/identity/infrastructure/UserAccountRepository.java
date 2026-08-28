package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailNormalized(String emailNormalized);

    @Query(value = "SELECT hashtext(:email) FROM "
            + "(SELECT pg_advisory_xact_lock(hashtext(:email))) AS acquired", nativeQuery = true)
    Integer lockNormalizedEmail(@Param("email") String emailNormalized);
}
