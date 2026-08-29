package br.com.controlegastos.ledger.infrastructure;

import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.ledger.domain.LedgerKind;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE envelope_id = :envelopeId AND kind = CAST(:kind AS VARCHAR) AND occurred_at <= :until", nativeQuery = true)
    java.math.BigDecimal sumAmountUpTo(@Param("envelopeId") UUID envelopeId, @Param("kind") String kind, @Param("until") LocalDate until);

    default java.math.BigDecimal sumAmountUpTo(UUID envelopeId, LedgerKind kind, LocalDate until) {
        return sumAmountUpTo(envelopeId, kind.name(), until);
    }

    Page<LedgerEntry> findByEnvelopeIdOrderByOccurredAtDescCreatedAtDesc(UUID envelopeId, Pageable pageable);

    Page<LedgerEntry> findByEnvelopeIdAndKindOrderByOccurredAtDescCreatedAtDesc(UUID envelopeId, LedgerKind kind, Pageable pageable);

    @Query("""
        SELECT e FROM LedgerEntry e WHERE e.envelopeId = :envelopeId
          AND (:kind IS NULL OR e.kind = :kind)
          AND (:monthStart IS NULL OR e.occurredAt >= :monthStart)
          AND (:monthEnd IS NULL OR e.occurredAt <= :monthEnd)
        ORDER BY e.occurredAt DESC, e.createdAt DESC
        """)
    Page<LedgerEntry> findFiltered(
            @Param("envelopeId") UUID envelopeId,
            @Param("kind") LedgerKind kind,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd,
            Pageable pageable);
}
