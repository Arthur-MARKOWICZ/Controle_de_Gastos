package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.MfaLoginChallenge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaLoginChallengeRepository extends JpaRepository<MfaLoginChallenge, UUID> {
}
