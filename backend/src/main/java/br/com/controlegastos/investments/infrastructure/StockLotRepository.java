package br.com.controlegastos.investments.infrastructure;

import br.com.controlegastos.investments.domain.StockLot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLotRepository extends JpaRepository<StockLot, UUID> {

    @Query("SELECT l FROM StockLot l WHERE l.ownerId = :ownerId ORDER BY l.purchaseDate, l.ticker, l.createdAt")
    List<StockLot> findListing(@Param("ownerId") UUID ownerId);

    Optional<StockLot> findByIdAndOwnerId(UUID id, UUID ownerId);
}
