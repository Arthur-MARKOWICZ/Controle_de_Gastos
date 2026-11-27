package br.com.controlegastos.investments.application;

import br.com.controlegastos.investments.domain.B3Ticker;

/** Fonte externa de cotações da B3 (porta do ADR-0034; provedor no ADR-0035). */
public interface QuoteProvider {

    /** Falso quando não há credencial: as rodadas ficam desligadas. */
    boolean isConfigured();

    /** Uma requisição para um ticker. Nunca lança exceção por falha do provedor. */
    QuoteResult fetch(B3Ticker ticker);
}
