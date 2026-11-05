package br.com.controlegastos.ledger.infrastructure;

import br.com.controlegastos.ledger.domain.LedgerEntryRevision;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRevisionRepository extends JpaRepository<LedgerEntryRevision, UUID> {
    Slice<LedgerEntryRevision> findByLedgerEntryIdOrderByChangedAtDescIdDesc(UUID ledgerEntryId, Pageable pageable);
}
