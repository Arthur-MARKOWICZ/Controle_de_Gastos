package br.com.controlegastos.investments.application;

/** Lote inexistente ou de outro titular: as duas situações respondem 404. */
public class StockLotNotFoundException extends RuntimeException {

    public StockLotNotFoundException() {
        super("Lote não encontrado");
    }
}
