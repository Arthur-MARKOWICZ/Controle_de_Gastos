package br.com.controlegastos.investments.application;

import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/** Resultado fechado de uma consulta ao provedor de cotações (ADR-0035, design D3). */
public sealed interface QuoteResult {

    /** Preço já normalizado a duas casas e o instante de mercado informado pelo provedor. */
    record Quoted(Money price, Instant quotedAt) implements QuoteResult {
        public Quoted {
            Objects.requireNonNull(price);
            Objects.requireNonNull(quotedAt);
        }
    }

    /** Ticker desconhecido, sem resultado ou com preço ausente, zero ou negativo. */
    record NotFound() implements QuoteResult {
    }

    /** Cota ou simultaneidade excedida (429): a rodada para. */
    record RateLimited() implements QuoteResult {
    }

    /** Credencial ou plano recusados (401, 403): a rodada para, sem repetir. */
    record Rejected(int status) implements QuoteResult {
    }

    /** Timeout, rede, 5xx, 400 ou corpo inválido; o código vai para o log de contagens. */
    record Unavailable(String code) implements QuoteResult {
    }
}
