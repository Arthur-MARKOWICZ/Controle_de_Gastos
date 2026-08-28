package br.com.controlegastos.income.infrastructure;

import br.com.controlegastos.income.domain.IncomeRevision;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeRevisionRepository extends JpaRepository<IncomeRevision, UUID> {
    Slice<IncomeRevision> findByOwnerIdOrderByChangedAtDescIdDesc(UUID ownerId, Pageable pageable);
}
