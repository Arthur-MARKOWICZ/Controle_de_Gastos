package br.com.controlegastos.investments.infrastructure;

import br.com.controlegastos.investments.domain.StockQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StockQuoteRepository extends JpaRepository<StockQuote, String> {

    @Query(value = "SELECT DISTINCT ticker FROM stock_lot ORDER BY ticker", nativeQuery = true)
    List<String> findHeldTickers();

    @Query(value = """
            SELECT DISTINCT l.ticker FROM stock_lot l
             WHERE NOT EXISTS (SELECT 1 FROM stock_quote q WHERE q.ticker = l.ticker)
             ORDER BY l.ticker
            """, nativeQuery = true)
    List<String> findMissingTickers();

    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM stock_quote q
             WHERE NOT EXISTS (SELECT 1 FROM stock_lot l WHERE l.ticker = q.ticker)
            """, nativeQuery = true)
    int deleteOrphans();

    /** Grava a cotação sem nunca trocar uma mais nova por uma mais antiga (ADR-0035). */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO stock_quote (ticker, price, quoted_at, fetched_at)
            VALUES (:ticker, :price, :quotedAt, :fetchedAt)
            ON CONFLICT (ticker) DO UPDATE
               SET price = EXCLUDED.price, quoted_at = EXCLUDED.quoted_at, fetched_at = EXCLUDED.fetched_at
             WHERE stock_quote.quoted_at <= EXCLUDED.quoted_at
            """, nativeQuery = true)
    int upsert(@Param("ticker") String ticker, @Param("price") BigDecimal price,
               @Param("quotedAt") Instant quotedAt, @Param("fetchedAt") Instant fetchedAt);
}
