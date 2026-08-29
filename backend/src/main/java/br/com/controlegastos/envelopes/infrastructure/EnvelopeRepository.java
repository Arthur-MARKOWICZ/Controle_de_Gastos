package br.com.controlegastos.envelopes.infrastructure;

import br.com.controlegastos.envelopes.domain.Envelope;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvelopeRepository extends JpaRepository<Envelope, UUID> {

    @Query("""
        SELECT e FROM Envelope e
        WHERE e.archivedAt IS NULL
          AND (e.ownerId = :userId OR EXISTS (
            SELECT 1 FROM EnvelopeParticipant ep WHERE ep.envelopeId = e.id AND ep.userId = :userId
          ))
        ORDER BY e.createdAt ASC
        """)
    List<Envelope> findVisibleByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT e FROM Envelope e
        WHERE e.ownerId = :userId OR EXISTS (
          SELECT 1 FROM EnvelopeParticipant ep WHERE ep.envelopeId = e.id AND ep.userId = :userId
        )
        ORDER BY e.createdAt ASC
        """)
    List<Envelope> findVisibleIncludingArchivedByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT COALESCE(SUM(base_amount), 0) FROM envelope WHERE owner_id = :ownerId AND archived_at IS NULL", nativeQuery = true)
    java.math.BigDecimal sumBaseAmountsRaw(@Param("ownerId") UUID ownerId);

    default br.com.controlegastos.shared.money.Money sumBaseAmounts(UUID ownerId) {
        java.math.BigDecimal raw = sumBaseAmountsRaw(ownerId);
        return br.com.controlegastos.shared.money.Money.brl(raw);
    }

    @Query("""
        SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Envelope e
        WHERE e.id = :envelopeId AND e.archivedAt IS NULL AND (
          e.ownerId = :userId OR EXISTS (
            SELECT 1 FROM EnvelopeParticipant ep WHERE ep.envelopeId = e.id AND ep.userId = :userId
          )
        )
        """)
    boolean isVisibleToUser(@Param("envelopeId") UUID envelopeId, @Param("userId") UUID userId);

    @Query("""
        SELECT CASE WHEN COUNT(ep) > 0 THEN true ELSE false END FROM EnvelopeParticipant ep
        WHERE ep.envelopeId = :envelopeId AND ep.userId = :userId
        """)
    boolean isParticipant(@Param("envelopeId") UUID envelopeId, @Param("userId") UUID userId);
}
