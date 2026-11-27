package br.com.controlegastos.investments.domain;

import br.com.controlegastos.shared.money.Money;
import br.com.controlegastos.shared.money.MoneyJpaConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Última cotação válida de um ticker da B3 (ADR-0034 e ADR-0035). Não pertence a
 * nenhum titular. Só é gravada pelo upsert do repositório, que nunca regride no tempo.
 */
@Entity
@Table(name = "stock_quote")
public class StockQuote {

    @Id
    @Column(length = 6)
    private String ticker;

    @Convert(converter = MoneyJpaConverter.class)
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money price;

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected StockQuote() {
    }

    public StockQuote(String ticker, Money price, Instant quotedAt, Instant fetchedAt) {
        this.ticker = ticker;
        this.price = price;
        this.quotedAt = quotedAt;
        this.fetchedAt = fetchedAt;
    }

    public String ticker() {
        return ticker;
    }

    public Money price() {
        return price;
    }

    public Instant quotedAt() {
        return quotedAt;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }
}
