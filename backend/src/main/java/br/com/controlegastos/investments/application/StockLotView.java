package br.com.controlegastos.investments.application;

import br.com.controlegastos.investments.domain.StockLot;
import br.com.controlegastos.investments.domain.StockQuote;
import br.com.controlegastos.shared.money.Money;

/** Lote com a última cotação do seu ticker, ou {@code null} se ainda não houver (ADR-0035). */
public record StockLotView(StockLot lot, StockQuote quote) {

    /**
     * {@code quantidade × cotação}, ou {@code null} sem cotação. Também {@code null} se o
     * produto passar do limite de {@link Money}, para uma cotação extrema não derrubar a listagem.
     */
    public Money currentValue() {
        if (quote == null) return null;
        try {
            return quote.price().multiply(lot.quantity());
        } catch (IllegalArgumentException beyondMoneyLimit) {
            return null;
        }
    }
}
