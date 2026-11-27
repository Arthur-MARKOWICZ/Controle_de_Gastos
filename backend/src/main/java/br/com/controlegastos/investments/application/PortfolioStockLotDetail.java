package br.com.controlegastos.investments.application;

import br.com.controlegastos.shared.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/** Detalhe de um lote dentro da posição agregada por ticker. */
public record PortfolioStockLotDetail(
        UUID id,
        long quantity,
        Money unitPrice,
        Money acquisitionCost,
        LocalDate purchaseDate,
        Money currentValue,
        Money pnl) {
}
