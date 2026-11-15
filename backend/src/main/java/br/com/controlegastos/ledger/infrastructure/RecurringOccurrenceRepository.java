package br.com.controlegastos.ledger.infrastructure;

import br.com.controlegastos.ledger.domain.RecurringOccurrence;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RecurringOccurrenceRepository extends JpaRepository<RecurringOccurrence, UUID> {

    /** Idempotente: a chave (recorrência, competência) garante uma ocorrência por mês. */
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO recurring_occurrence
            (id, recurring_entry_id, envelope_id, competence, due_date, suggested_amount, status, created_at, version)
        VALUES (:id, :recurringEntryId, :envelopeId, :competence, :dueDate, :suggestedAmount, 'PENDING', :createdAt, 0)
        ON CONFLICT (recurring_entry_id, competence) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("recurringEntryId") UUID recurringEntryId,
                       @Param("envelopeId") UUID envelopeId,
                       @Param("competence") LocalDate competence,
                       @Param("dueDate") LocalDate dueDate,
                       @Param("suggestedAmount") BigDecimal suggestedAmount,
                       @Param("createdAt") Instant createdAt);

    @Query(value = """
        SELECT o.id AS id, o.recurring_entry_id AS recurringEntryId, o.envelope_id AS envelopeId,
               e.name AS envelopeName, r.kind AS kind, r.description AS description,
               o.suggested_amount AS suggestedAmount, o.due_date AS dueDate, o.competence AS competence
        FROM recurring_occurrence o
        JOIN recurring_entry r ON r.id = o.recurring_entry_id
        JOIN envelope e ON e.id = o.envelope_id
        WHERE e.owner_id = :ownerId AND e.archived_at IS NULL AND o.status = 'PENDING'
        ORDER BY o.due_date, e.name
        """, nativeQuery = true)
    List<PendingOccurrenceView> findPendingForOwner(@Param("ownerId") UUID ownerId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE recurring_occurrence SET status = 'DISCARDED', resolved_at = :now, version = version + 1
        WHERE envelope_id = :envelopeId AND status = 'PENDING'
        """, nativeQuery = true)
    int discardPendingByEnvelope(@Param("envelopeId") UUID envelopeId, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE recurring_occurrence SET status = 'DISCARDED', resolved_at = :now, version = version + 1
        WHERE recurring_entry_id = :recurringEntryId AND status = 'PENDING'
        """, nativeQuery = true)
    int discardPendingByRecurrence(@Param("recurringEntryId") UUID recurringEntryId, @Param("now") Instant now);

    interface PendingOccurrenceView {
        UUID getId();
        UUID getRecurringEntryId();
        UUID getEnvelopeId();
        String getEnvelopeName();
        String getKind();
        String getDescription();
        BigDecimal getSuggestedAmount();
        LocalDate getDueDate();
        LocalDate getCompetence();
    }
}
