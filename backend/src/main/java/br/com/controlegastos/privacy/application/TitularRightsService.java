package br.com.controlegastos.privacy.application;

import br.com.controlegastos.identity.application.AccountClosureService;
import br.com.controlegastos.identity.application.AuthenticationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TitularRightsService {

    private final AuthenticationService authentication;
    private final AccountClosureService accountClosure;
    private final JdbcTemplate jdbc;

    public TitularRightsService(
            AuthenticationService authentication,
            AccountClosureService accountClosure,
            JdbcTemplate jdbc) {
        this.authentication = authentication;
        this.accountClosure = accountClosure;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportCurrentTitular() {
        UUID userId = authentication.currentUserId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identity", accountClosure.exportIdentity(userId));
        payload.put("income", exportIncome(userId));
        payload.put("envelopes", exportEnvelopes(userId));
        payload.put("ledgerEntries", exportLedger(userId));
        payload.put("ledgerEntryRevisions", exportLedgerRevisions(userId));
        payload.put("recurringEntries", exportRecurringEntries(userId));
        payload.put("recurringOccurrences", exportRecurringOccurrences(userId));
        return payload;
    }

    @Transactional
    public void eraseCurrentTitular(String password) {
        UUID userId = authentication.currentUserId();
        eraseFinancialData(userId);
        accountClosure.closeAccount(userId, password);
    }

    private void eraseFinancialData(UUID userId) {
        jdbc.update("DELETE FROM envelope WHERE owner_id = ?", userId);
        jdbc.update("DELETE FROM envelope_participant WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM income_revision WHERE owner_id = ?", userId);
        jdbc.update("DELETE FROM monthly_income WHERE owner_id = ?", userId);
    }

    private List<Map<String, Object>> exportIncome(UUID userId) {
        return jdbc.query(
                """
                SELECT effective_month, amount::text AS amount, updated_at, version
                FROM monthly_income
                WHERE owner_id = ?
                ORDER BY effective_month
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("effectiveMonth", rs.getDate("effective_month").toLocalDate().toString());
                    row.put("amount", rs.getString("amount"));
                    row.put("currency", "BRL");
                    row.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
                    row.put("version", rs.getLong("version"));
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> exportEnvelopes(UUID userId) {
        return jdbc.query(
                """
                SELECT id, name, purpose, base_amount::text AS base_amount, created_at, archived_at
                FROM envelope
                WHERE owner_id = ?
                ORDER BY created_at
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("name", rs.getString("name"));
                    row.put("purpose", rs.getString("purpose"));
                    row.put("baseAmount", rs.getString("base_amount"));
                    row.put("currency", "BRL");
                    row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    row.put("archivedAt", rs.getTimestamp("archived_at") == null
                            ? null
                            : rs.getTimestamp("archived_at").toInstant().toString());
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> exportLedger(UUID userId) {
        return jdbc.query(
                """
                SELECT id, envelope_id, kind, amount::text AS amount, occurred_at, description,
                       author_id, created_at, deleted_at
                FROM ledger_entry
                WHERE owner_id = ? OR author_id = ?
                ORDER BY occurred_at, created_at
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("envelopeId", rs.getObject("envelope_id", UUID.class).toString());
                    row.put("kind", rs.getString("kind"));
                    row.put("amount", rs.getString("amount"));
                    row.put("currency", "BRL");
                    row.put("occurredAt", rs.getDate("occurred_at").toLocalDate().toString());
                    row.put("description", rs.getString("description"));
                    row.put("authorId", rs.getObject("author_id", UUID.class).toString());
                    row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    row.put("deletedAt", rs.getTimestamp("deleted_at") == null
                            ? null
                            : rs.getTimestamp("deleted_at").toInstant().toString());
                    return row;
                },
                userId,
                userId);
    }

    private List<Map<String, Object>> exportRecurringEntries(UUID userId) {
        return jdbc.query(
                """
                SELECT r.id, r.envelope_id, r.kind, r.amount::text AS amount, r.day_of_month, r.description,
                       r.active, r.first_competence, r.created_at, r.deleted_at
                FROM recurring_entry r
                JOIN envelope e ON e.id = r.envelope_id
                WHERE e.owner_id = ?
                ORDER BY r.created_at
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("envelopeId", rs.getObject("envelope_id", UUID.class).toString());
                    row.put("kind", rs.getString("kind"));
                    row.put("amount", rs.getString("amount"));
                    row.put("currency", "BRL");
                    row.put("dayOfMonth", rs.getInt("day_of_month"));
                    row.put("description", rs.getString("description"));
                    row.put("active", rs.getBoolean("active"));
                    row.put("firstCompetence", rs.getDate("first_competence").toLocalDate().toString().substring(0, 7));
                    row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    row.put("deletedAt", rs.getTimestamp("deleted_at") == null
                            ? null
                            : rs.getTimestamp("deleted_at").toInstant().toString());
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> exportRecurringOccurrences(UUID userId) {
        return jdbc.query(
                """
                SELECT o.id, o.recurring_entry_id, o.competence, o.due_date, o.suggested_amount::text AS suggested_amount,
                       o.status, o.ledger_entry_id, o.resolved_at, o.created_at
                FROM recurring_occurrence o
                JOIN envelope e ON e.id = o.envelope_id
                WHERE e.owner_id = ?
                ORDER BY o.due_date, o.created_at
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("recurringEntryId", rs.getObject("recurring_entry_id", UUID.class).toString());
                    row.put("competence", rs.getDate("competence").toLocalDate().toString().substring(0, 7));
                    row.put("dueDate", rs.getDate("due_date").toLocalDate().toString());
                    row.put("suggestedAmount", rs.getString("suggested_amount"));
                    row.put("currency", "BRL");
                    row.put("status", rs.getString("status"));
                    UUID ledgerEntryId = rs.getObject("ledger_entry_id", UUID.class);
                    row.put("ledgerEntryId", ledgerEntryId == null ? null : ledgerEntryId.toString());
                    row.put("resolvedAt", rs.getTimestamp("resolved_at") == null
                            ? null
                            : rs.getTimestamp("resolved_at").toInstant().toString());
                    row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    return row;
                },
                userId);
    }

    private List<Map<String, Object>> exportLedgerRevisions(UUID userId) {
        return jdbc.query(
                """
                SELECT id, ledger_entry_id, action, actor_user_id, previous_envelope_id,
                       previous_amount::text AS previous_amount, previous_description, changed_at
                FROM ledger_entry_revision
                WHERE owner_id = ? OR actor_user_id = ?
                ORDER BY changed_at, id
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class).toString());
                    row.put("ledgerEntryId", rs.getObject("ledger_entry_id", UUID.class).toString());
                    row.put("action", rs.getString("action"));
                    row.put("actorUserId", rs.getObject("actor_user_id", UUID.class).toString());
                    row.put("previousEnvelopeId", rs.getObject("previous_envelope_id", UUID.class).toString());
                    row.put("previousAmount", rs.getString("previous_amount"));
                    row.put("currency", "BRL");
                    row.put("previousDescription", rs.getString("previous_description"));
                    row.put("changedAt", rs.getTimestamp("changed_at").toInstant().toString());
                    return row;
                },
                userId,
                userId);
    }
}
