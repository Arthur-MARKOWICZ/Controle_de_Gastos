package br.com.controlegastos.investments.application;

import br.com.controlegastos.shared.money.Money;
import java.util.UUID;

/** Posição de CDB no resumo: principal, estimado e P&L. */
public record PortfolioCdbPosition(
        UUID id,
        Money principal,
        Money estimatedValue,
        Money pnl) {
}
