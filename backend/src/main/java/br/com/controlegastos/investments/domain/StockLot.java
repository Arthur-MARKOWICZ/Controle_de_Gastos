package br.com.controlegastos.investments.domain;

import br.com.controlegastos.shared.money.Money;
import br.com.controlegastos.shared.money.MoneyJpaConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Uma compra de ativo da B3 registrada à mão pelo titular (ADR-0034). */
@Entity
@Table(name = "stock_lot")
public class StockLot {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 6)
    private String ticker;

    @Column(nullable = false)
    private long quantity;

    @Convert(converter = MoneyJpaConverter.class)
    @Column(name = "unit_price", nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money unitPrice;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockLot() {
    }

    /**
     * @param today data atual em America/Sao_Paulo; a compra não pode ser posterior a ela
     */
    public static StockLot register(UUID ownerId, B3Ticker ticker, long quantity, Money unitPrice,
                                    LocalDate purchaseDate, LocalDate today, Instant now) {
        Objects.requireNonNull(ownerId, "O titular é obrigatório");
        Objects.requireNonNull(ticker, "O ticker é obrigatório");
        if (quantity <= 0) throw new IllegalArgumentException("A quantidade deve ser maior que zero");
        if (unitPrice == null || unitPrice.compareTo(Money.zero()) <= 0) {
            throw new IllegalArgumentException("O preço unitário deve ser maior que zero");
        }
        if (purchaseDate == null || purchaseDate.isAfter(today)) {
            throw new IllegalArgumentException("A data da compra não pode ser futura");
        }
        unitPrice.multiply(quantity); // recusa custo acima do limite de Money antes de gravar
        StockLot lot = new StockLot();
        lot.id = UUID.randomUUID();
        lot.ownerId = ownerId;
        lot.ticker = ticker.value();
        lot.quantity = quantity;
        lot.unitPrice = unitPrice;
        lot.purchaseDate = purchaseDate;
        lot.createdAt = Objects.requireNonNull(now);
        return lot;
    }

    public Money acquisitionCost() {
        return unitPrice.multiply(quantity);
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ticker() {
        return ticker;
    }

    public long quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public LocalDate purchaseDate() {
        return purchaseDate;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
