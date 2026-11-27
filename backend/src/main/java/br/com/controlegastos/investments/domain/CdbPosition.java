package br.com.controlegastos.investments.domain;

import br.com.controlegastos.shared.money.Money;
import br.com.controlegastos.shared.money.MoneyJpaConverter;
import ch.obermuhlner.math.big.BigDecimalMath;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Posição de CDB prefixado do titular; o estimado é recalculado na leitura (ADR-0034). */
@Entity
@Table(name = "cdb_position")
public class CdbPosition {

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Convert(converter = MoneyJpaConverter.class)
    @Column(nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private Money principal;

    @Column(name = "annual_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal annualRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CdbPosition() {
    }

    /**
     * @param today data atual em America/Sao_Paulo; o início não pode ser posterior a ela
     */
    public static CdbPosition register(UUID ownerId, Money principal, AnnualRate annualRate,
                                       LocalDate startDate, LocalDate maturityDate,
                                       LocalDate today, Instant now) {
        Objects.requireNonNull(ownerId, "O titular é obrigatório");
        Objects.requireNonNull(annualRate, "A taxa anual é obrigatória");
        if (principal == null || principal.compareTo(Money.zero()) <= 0) {
            throw new IllegalArgumentException("O principal deve ser maior que zero");
        }
        if (startDate == null || startDate.isAfter(today)) {
            throw new IllegalArgumentException("A data de início não pode ser futura");
        }
        if (maturityDate == null || !maturityDate.isAfter(startDate)) {
            throw new IllegalArgumentException("A data de vencimento deve ser posterior ao início");
        }
        CdbPosition position = new CdbPosition();
        position.id = UUID.randomUUID();
        position.ownerId = ownerId;
        position.principal = principal;
        position.annualRate = annualRate.value();
        position.startDate = startDate;
        position.maturityDate = maturityDate;
        position.createdAt = Objects.requireNonNull(now);
        return position;
    }

    /**
     * Estimativa bruta (sem IR/IOF): principal × (1 + taxa) ^ (dias / 365),
     * com dias até {@code min(today, maturityDate)}. Arredonda só o resultado (HALF_EVEN).
     */
    public Money estimatedValue(LocalDate today) {
        Objects.requireNonNull(today, "A data de referência é obrigatória");
        LocalDate accrualEnd = today.isBefore(maturityDate) ? today : maturityDate;
        long days = ChronoUnit.DAYS.between(startDate, accrualEnd);
        if (days <= 0) {
            return principal;
        }
        BigDecimal exponent = BigDecimal.valueOf(days).divide(DAYS_IN_YEAR, MathContext.DECIMAL128);
        BigDecimal factor = BigDecimalMath.pow(
                BigDecimal.ONE.add(annualRate), exponent, MathContext.DECIMAL128);
        BigDecimal estimated = principal.amount().multiply(factor, MathContext.DECIMAL128)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN);
        return Money.brl(estimated);
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Money principal() {
        return principal;
    }

    public BigDecimal annualRate() {
        return annualRate;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate maturityDate() {
        return maturityDate;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
