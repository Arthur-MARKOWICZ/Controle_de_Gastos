package br.com.controlegastos.investments.application;

import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.infrastructure.StockQuoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Atualiza a última cotação válida dos tickers que têm lote (ADR-0034 e ADR-0035).
 * Um ticker por vez e nunca duas rodadas juntas: o plano gratuito aceita uma
 * requisição simultânea. Os logs levam só contagens e códigos, nunca ticker com preço.
 */
@Component
public class StockQuoteRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(StockQuoteRefresher.class);
    private static final String ZONE = "America/Sao_Paulo";
    private static final int MAX_CONSECUTIVE_UNAVAILABLE = 3;

    private final StockQuoteRepository quotes;
    private final QuoteProvider provider;
    private final Clock clock;
    private final int maxTickers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StockQuoteRefresher(StockQuoteRepository quotes, QuoteProvider provider, Clock clock,
                               @Value("${app.investments.quotes.max-tickers:30}") int maxTickers) {
        this.quotes = quotes;
        this.provider = provider;
        this.clock = clock;
        this.maxTickers = maxTickers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStatus() {
        if (!provider.isConfigured()) LOG.info("quote_refresh disabled reason=no_token");
    }

    /** Rodada do pregão: todos os tickers com lote. */
    @Scheduled(cron = "${app.investments.quotes.trading-cron}", zone = ZONE)
    public void refreshHeld() {
        run(Mode.HELD);
    }

    /** Rodada horária: só os tickers com lote que ainda não têm cotação. */
    @Scheduled(cron = "${app.investments.quotes.missing-cron}", zone = ZONE)
    public void refreshMissing() {
        run(Mode.MISSING);
    }

    private enum Mode { HELD, MISSING }

    private void run(Mode mode) {
        if (!provider.isConfigured()) return;
        if (!running.compareAndSet(false, true)) {
            LOG.info("quote_refresh mode={} skipped reason=busy", label(mode));
            return;
        }
        try {
            refresh(mode);
        } catch (RuntimeException failure) {
            LOG.warn("quote_refresh mode={} failed error={}", label(mode), failure.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    private void refresh(Mode mode) {
        int orphans = quotes.deleteOrphans();
        List<String> tickers = mode == Mode.HELD ? quotes.findHeldTickers() : quotes.findMissingTickers();
        if (tickers.size() > maxTickers) {
            LOG.warn("quote_budget_exceeded tickers={} limit={}", tickers.size(), maxTickers);
        }
        int updated = 0;
        int notFound = 0;
        int failed = 0;
        int consecutiveUnavailable = 0;
        var failureCodes = new TreeSet<String>();
        String stopped = "none";
        for (String ticker : tickers) {
            QuoteResult result = provider.fetch(B3Ticker.of(ticker));
            if (result instanceof QuoteResult.Unavailable unavailable) {
                failed++;
                failureCodes.add(unavailable.code());
                if (++consecutiveUnavailable >= MAX_CONSECUTIVE_UNAVAILABLE) {
                    stopped = "unavailable";
                    break;
                }
                continue;
            }
            consecutiveUnavailable = 0;
            switch (result) {
                case QuoteResult.Quoted quoted -> {
                    Instant now = clock.instant();
                    quotes.upsert(ticker, quoted.price().amount(), quoted.quotedAt(), now);
                    updated++;
                }
                case QuoteResult.NotFound ignored -> notFound++;
                case QuoteResult.RateLimited ignored -> stopped = "rate_limited";
                case QuoteResult.Rejected rejected -> stopped = "rejected_" + rejected.status();
                case QuoteResult.Unavailable ignored -> throw new IllegalStateException("tratado acima");
            }
            if (!"none".equals(stopped)) break;
        }
        LOG.info("quote_refresh mode={} tickers={} updated={} not_found={} failed={} failure_codes={} "
                        + "orphans_removed={} stopped={}",
                label(mode), tickers.size(), updated, notFound, failed, failureCodes, orphans, stopped);
    }

    private static String label(Mode mode) {
        return mode.name().toLowerCase(java.util.Locale.ROOT);
    }
}
