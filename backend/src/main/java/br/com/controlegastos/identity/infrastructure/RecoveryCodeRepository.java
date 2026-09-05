package br.com.controlegastos.identity.infrastructure;

import br.com.controlegastos.identity.domain.RecoveryCode;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {
}
