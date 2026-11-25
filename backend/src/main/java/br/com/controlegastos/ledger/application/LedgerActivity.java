package br.com.controlegastos.ledger.application;

import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.ledger.infrastructure.LedgerEntryRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de atividade usada pelo lembrete diário (ADR-0033); não expõe valores. */
@Service
@NamedInterface("activity")
public class LedgerActivity {

    private final LedgerEntryRepository entries;

    LedgerActivity(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    /** Há gasto não excluído de autoria da pessoa na data informada? */
    @Transactional(readOnly = true)
    public boolean hasExpenseOn(UUID authorId, LocalDate date) {
        return entries.existsByAuthorIdAndKindAndOccurredAtAndDeletedAtIsNull(authorId, LedgerKind.EXPENSE, date);
    }
}
