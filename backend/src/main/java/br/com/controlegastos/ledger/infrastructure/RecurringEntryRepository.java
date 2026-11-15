package br.com.controlegastos.ledger.infrastructure;

import br.com.controlegastos.ledger.domain.RecurringEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RecurringEntryRepository extends JpaRepository<RecurringEntry, UUID> {

    @Query("SELECT r FROM RecurringEntry r WHERE r.envelopeId = :envelopeId AND r.deletedAt IS NULL ORDER BY r.dayOfMonth, r.createdAt")
    List<RecurringEntry> findActiveListing(@Param("envelopeId") UUID envelopeId);

    @Query("SELECT r FROM RecurringEntry r WHERE r.id = :id AND r.envelopeId = :envelopeId AND r.deletedAt IS NULL")
    Optional<RecurringEntry> findInEnvelope(@Param("id") UUID id, @Param("envelopeId") UUID envelopeId);

    /** Recorrências que podem gerar a competência informada: ativas, não excluídas e de verbas não arquivadas. */
    @Query(value = """
        SELECT r.* FROM recurring_entry r
        JOIN envelope e ON e.id = r.envelope_id
        WHERE r.active AND r.deleted_at IS NULL AND e.archived_at IS NULL
          AND r.first_competence <= :competence
        """, nativeQuery = true)
    List<RecurringEntry> findGenerationCandidates(@Param("competence") LocalDate competence);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE recurring_entry SET active = false, version = version + 1 WHERE envelope_id = :envelopeId AND active", nativeQuery = true)
    int deactivateByEnvelope(@Param("envelopeId") UUID envelopeId);
}
