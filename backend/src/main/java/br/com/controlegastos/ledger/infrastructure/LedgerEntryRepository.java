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

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE envelope_id = :envelopeId AND kind = CAST(:kind AS VARCHAR) AND occurred_at <= :until AND deleted_at IS NULL", nativeQuery = true)
    java.math.BigDecimal sumAmountUpTo(@Param("envelopeId") UUID envelopeId, @Param("kind") String kind, @Param("until") LocalDate until);

    default java.math.BigDecimal sumAmountUpTo(UUID envelopeId, LedgerKind kind, LocalDate until) {
        return sumAmountUpTo(envelopeId, kind.name(), until);
    }

    @Query(value = """
        SELECT COALESCE(SUM(amount), 0) FROM ledger_entry
        WHERE envelope_id = :envelopeId AND kind = CAST(:kind AS VARCHAR)
          AND occurred_at >= :from AND occurred_at <= :until AND deleted_at IS NULL
        """, nativeQuery = true)
    java.math.BigDecimal sumAmountBetween(
            @Param("envelopeId") UUID envelopeId,
            @Param("kind") String kind,
            @Param("from") LocalDate from,
            @Param("until") LocalDate until);

    default java.math.BigDecimal sumAmountBetween(UUID envelopeId, LedgerKind kind, LocalDate from, LocalDate until) {
        return sumAmountBetween(envelopeId, kind.name(), from, until);
    }

    Page<LedgerEntry> findByEnvelopeIdOrderByOccurredAtDescCreatedAtDesc(UUID envelopeId, Pageable pageable);

    Page<LedgerEntry> findByEnvelopeIdAndKindOrderByOccurredAtDescCreatedAtDesc(UUID envelopeId, LedgerKind kind, Pageable pageable);

    @Query("""
        SELECT e FROM LedgerEntry e WHERE e.envelopeId = :envelopeId
          AND e.deletedAt IS NULL
          AND (:filterKind = false OR e.kind = :kind)
          AND (:filterMonth = false OR e.occurredAt >= :monthStart)
          AND (:filterMonth = false OR e.occurredAt <= :monthEnd)
        ORDER BY e.occurredAt DESC, e.createdAt DESC
        """)
    Page<LedgerEntry> findFiltered(
            @Param("envelopeId") UUID envelopeId,
            @Param("filterKind") boolean filterKind,
            @Param("kind") LedgerKind kind,
            @Param("filterMonth") boolean filterMonth,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd,
            Pageable pageable);

    default Page<LedgerEntry> findFiltered(
            UUID envelopeId,
            LedgerKind kind,
            LocalDate monthStart,
            LocalDate monthEnd,
            Pageable pageable) {
        boolean filterKind = kind != null;
        boolean filterMonth = monthStart != null && monthEnd != null;
        return findFiltered(
                envelopeId,
                filterKind,
                filterKind ? kind : LedgerKind.EXPENSE,
                filterMonth,
                filterMonth ? monthStart : LocalDate.EPOCH,
                filterMonth ? monthEnd : LocalDate.EPOCH,
                pageable);
    }

    /** Modo do filtro de autor do histórico: todos, só a pessoa autenticada ou só outras pessoas. */
    enum AuthorFilter { ANY, ME, OTHERS }

    /**
     * Filtros opcionais do histórico, já normalizados: {@code textPattern} é o padrão
     * {@code LIKE} em minúsculas com curingas escapados; nulos desligam o filtro.
     */
    record HistoryFilter(String textPattern, UUID envelopeId, AuthorFilter author) {
        public static final HistoryFilter NONE = new HistoryFilter(null, null, AuthorFilter.ANY);
    }

    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.kind = br.com.controlegastos.ledger.domain.LedgerKind.EXPENSE
          AND e.occurredAt BETWEEN :from AND :to
          AND (
            e.ownerId = :userId OR EXISTS (
              SELECT 1 FROM EnvelopeParticipant participant
              WHERE participant.envelopeId = e.envelopeId AND participant.userId = :userId
            )
          )
          AND (
            e.deletedAt IS NULL OR (:includeDeleted = true AND e.ownerId = :userId)
          )
          AND (:filterText = false OR LOWER(e.description) LIKE :pattern ESCAPE '\\')
          AND (:filterEnvelope = false OR e.envelopeId = :envelopeId)
          AND (:author = 'ANY' OR (:author = 'ME' AND e.authorId = :userId) OR (:author = 'OTHERS' AND e.authorId <> :userId))
        ORDER BY e.occurredAt DESC, e.createdAt DESC
        """)
    Page<LedgerEntry> findHistory(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("filterText") boolean filterText,
            @Param("pattern") String pattern,
            @Param("filterEnvelope") boolean filterEnvelope,
            @Param("envelopeId") UUID envelopeId,
            @Param("author") String author,
            Pageable pageable);

    default Page<LedgerEntry> findHistory(
            UUID userId, LocalDate from, LocalDate to, boolean includeDeleted, HistoryFilter filter, Pageable pageable) {
        return findHistory(userId, from, to, includeDeleted,
                filter.textPattern() != null, filter.textPattern() != null ? filter.textPattern() : "",
                filter.envelopeId() != null, filter.envelopeId() != null ? filter.envelopeId() : userId,
                filter.author().name(), pageable);
    }

    /** Soma dos gastos ativos do histórico com os mesmos filtros de {@link #findHistory}. */
    @Query(value = """
        SELECT COALESCE(SUM(e.amount), 0) FROM ledger_entry e
        WHERE e.kind = 'EXPENSE'
          AND e.occurred_at BETWEEN :from AND :to
          AND e.deleted_at IS NULL
          AND (
            e.owner_id = :userId OR EXISTS (
              SELECT 1 FROM envelope_participant participant
              WHERE participant.envelope_id = e.envelope_id AND participant.user_id = :userId
            )
          )
          AND (:filterText = false OR lower(e.description) LIKE :pattern ESCAPE '\\')
          AND (:filterEnvelope = false OR e.envelope_id = :envelopeId)
          AND (CAST(:author AS VARCHAR) = 'ANY'
               OR (CAST(:author AS VARCHAR) = 'ME' AND e.author_id = :userId)
               OR (CAST(:author AS VARCHAR) = 'OTHERS' AND e.author_id <> :userId))
        """, nativeQuery = true)
    java.math.BigDecimal sumHistory(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("filterText") boolean filterText,
            @Param("pattern") String pattern,
            @Param("filterEnvelope") boolean filterEnvelope,
            @Param("envelopeId") UUID envelopeId,
            @Param("author") String author);

    default java.math.BigDecimal sumHistory(UUID userId, LocalDate from, LocalDate to, HistoryFilter filter) {
        return sumHistory(userId, from, to,
                filter.textPattern() != null, filter.textPattern() != null ? filter.textPattern() : "",
                filter.envelopeId() != null, filter.envelopeId() != null ? filter.envelopeId() : userId,
                filter.author().name());
    }

    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.kind = br.com.controlegastos.ledger.domain.LedgerKind.EXPENSE
          AND e.occurredAt BETWEEN :from AND :to
          AND e.deletedAt IS NULL
          AND (
            e.ownerId = :userId OR EXISTS (
              SELECT 1 FROM EnvelopeParticipant participant
              WHERE participant.envelopeId = e.envelopeId AND participant.userId = :userId
            )
          )
        ORDER BY e.occurredAt ASC, e.createdAt ASC
        """)
    java.util.List<LedgerEntry> findActiveHistory(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.occurredAt BETWEEN :from AND :to
          AND e.deletedAt IS NULL
          AND (
            e.ownerId = :userId OR EXISTS (
              SELECT 1 FROM EnvelopeParticipant participant
              WHERE participant.envelopeId = e.envelopeId AND participant.userId = :userId
            )
          )
        ORDER BY e.occurredAt ASC, e.createdAt ASC
        """)
    java.util.List<LedgerEntry> findActiveVisibleEntries(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    boolean existsByAuthorIdAndKindAndOccurredAtAndDeletedAtIsNull(
            UUID authorId, br.com.controlegastos.ledger.domain.LedgerKind kind, java.time.LocalDate occurredAt);

    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.kind = br.com.controlegastos.ledger.domain.LedgerKind.EXPENSE
          AND e.authorId = :authorId
          AND e.deletedAt IS NULL
          AND e.occurredAt BETWEEN :from AND :to
        ORDER BY e.createdAt DESC
        """)
    java.util.List<LedgerEntry> findActiveExpensesByAuthorBetween(
            @Param("authorId") UUID authorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
