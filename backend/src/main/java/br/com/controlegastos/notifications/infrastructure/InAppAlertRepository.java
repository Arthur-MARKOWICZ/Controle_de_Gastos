package br.com.controlegastos.notifications.infrastructure;

import br.com.controlegastos.notifications.domain.InAppAlert;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InAppAlertRepository extends JpaRepository<InAppAlert, UUID> {

    @Query("""
            select a from InAppAlert a
            where a.ownerId = :ownerId
              and a.envelopeId = :envelopeId
              and a.referenceMonth = :referenceMonth
              and a.type = br.com.controlegastos.notifications.domain.AlertType.NEGATIVE_BALANCE
              and a.dismissedAt is null
              and a.resolvedAt is null
            """)
    Optional<InAppAlert> findActiveNegative(
            @Param("ownerId") UUID ownerId,
            @Param("envelopeId") UUID envelopeId,
            @Param("referenceMonth") LocalDate referenceMonth
    );

    @Query("""
            select a from InAppAlert a
            where a.ownerId = :ownerId
              and a.dismissedAt is null
              and a.resolvedAt is null
            order by a.createdAt desc
            """)
    List<InAppAlert> findActiveByOwner(@Param("ownerId") UUID ownerId);
}
