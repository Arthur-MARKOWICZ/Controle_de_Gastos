package br.com.controlegastos.investments.infrastructure;

import br.com.controlegastos.investments.domain.CdbPosition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CdbPositionRepository extends JpaRepository<CdbPosition, UUID> {

    @Query("SELECT c FROM CdbPosition c WHERE c.ownerId = :ownerId ORDER BY c.startDate, c.createdAt")
    List<CdbPosition> findListing(@Param("ownerId") UUID ownerId);

    Optional<CdbPosition> findByIdAndOwnerId(UUID id, UUID ownerId);
}
