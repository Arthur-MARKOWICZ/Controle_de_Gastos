package br.com.controlegastos.investments.application;

import br.com.controlegastos.investments.domain.CdbPosition;
import br.com.controlegastos.shared.money.Money;
import java.time.LocalDate;

/** Posição de CDB com o estimado recalculado na data de referência. */
public record CdbPositionView(CdbPosition position, Money estimatedValue) {

    public static CdbPositionView of(CdbPosition position, LocalDate today) {
        return new CdbPositionView(position, position.estimatedValue(today));
    }
}
