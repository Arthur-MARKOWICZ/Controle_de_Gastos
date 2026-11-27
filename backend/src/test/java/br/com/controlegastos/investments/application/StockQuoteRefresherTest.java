package br.com.controlegastos.investments.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.infrastructure.StockQuoteRepository;
import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Rodadas de atualização de cotações (spec investments/stock-quotes, ADR-0035, design D5 e D6). */
@ExtendWith(MockitoExtension.class)
class StockQuoteRefresherTest {

    private static final Instant NOW = Instant.parse("2026-11-26T19:31:00Z");
    private static final Instant MARKET = Instant.parse("2026-11-26T19:00:00Z");

    @Mock StockQuoteRepository quotes;
    @Mock QuoteProvider provider;

    private StockQuoteRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new StockQuoteRefresher(quotes, provider, Clock.fixed(NOW, ZoneOffset.UTC), 30);
        lenient().when(provider.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("rodada do pregão consulta cada ticker mantido uma vez e grava as cotações")
    void refreshHeldUpdatesEveryTicker() {
        when(quotes.findHeldTickers()).thenReturn(List.of("PETR4", "VALE3"));
        when(provider.fetch(B3Ticker.of("PETR4"))).thenReturn(quoted("41.20"));
        when(provider.fetch(B3Ticker.of("VALE3"))).thenReturn(quoted("60.05"));

        refresher.refreshHeld();

        verify(provider, times(1)).fetch(B3Ticker.of("PETR4"));
        verify(quotes).upsert("PETR4", new BigDecimal("41.20"), MARKET, NOW);
        verify(quotes).upsert("VALE3", new BigDecimal("60.05"), MARKET, NOW);
    }

    @Test
    @DisplayName("limpeza de órfãos roda antes das consultas")
    void orphansAreDeletedFirst() {
        when(quotes.findHeldTickers()).thenReturn(List.of("PETR4"));
        when(provider.fetch(any())).thenReturn(quoted("41.20"));

        refresher.refreshHeld();

        InOrder order = inOrder(quotes, provider);
        order.verify(quotes).deleteOrphans();
        order.verify(quotes).findHeldTickers();
        order.verify(provider).fetch(any());
    }

    @Test
    @DisplayName("ticker desconhecido fica sem cotação e a rodada segue")
    void notFoundContinues() {
        when(quotes.findHeldTickers()).thenReturn(List.of("ABCD3", "PETR4"));
        when(provider.fetch(B3Ticker.of("ABCD3"))).thenReturn(new QuoteResult.NotFound());
        when(provider.fetch(B3Ticker.of("PETR4"))).thenReturn(quoted("41.20"));

        refresher.refreshHeld();

        verify(quotes, never()).upsert(eq("ABCD3"), any(), any(), any());
        verify(quotes).upsert("PETR4", new BigDecimal("41.20"), MARKET, NOW);
    }

    @Test
    @DisplayName("limite de requisições para a rodada sem consultar os restantes")
    void rateLimitStops() {
        when(quotes.findHeldTickers()).thenReturn(List.of("ITUB4", "PETR4", "VALE3"));
        when(provider.fetch(B3Ticker.of("ITUB4"))).thenReturn(quoted("33.00"));
        when(provider.fetch(B3Ticker.of("PETR4"))).thenReturn(new QuoteResult.RateLimited());

        refresher.refreshHeld();

        verify(provider, never()).fetch(B3Ticker.of("VALE3"));
        verify(quotes, times(1)).upsert(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("credencial recusada para a rodada e não altera cotações")
    void rejectedStops() {
        when(quotes.findHeldTickers()).thenReturn(List.of("PETR4", "VALE3"));
        when(provider.fetch(B3Ticker.of("PETR4"))).thenReturn(new QuoteResult.Rejected(401));

        refresher.refreshHeld();

        verify(provider, never()).fetch(B3Ticker.of("VALE3"));
        verify(quotes, never()).upsert(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("três indisponibilidades seguidas param a rodada")
    void threeConsecutiveUnavailableStop() {
        when(quotes.findHeldTickers()).thenReturn(List.of("AAAA3", "BBBB3", "CCCC3", "DDDD3"));
        when(provider.fetch(any())).thenReturn(new QuoteResult.Unavailable("io"));

        refresher.refreshHeld();

        verify(provider, times(3)).fetch(any());
        verify(provider, never()).fetch(B3Ticker.of("DDDD3"));
    }

    @Test
    @DisplayName("um sucesso zera a contagem de indisponibilidades seguidas")
    void successResetsUnavailableStreak() {
        when(quotes.findHeldTickers()).thenReturn(List.of("AAAA3", "BBBB3", "CCCC3", "DDDD3", "EEEE3"));
        when(provider.fetch(any())).thenReturn(new QuoteResult.Unavailable("io"));
        when(provider.fetch(B3Ticker.of("CCCC3"))).thenReturn(quoted("10.00"));

        refresher.refreshHeld();

        verify(provider, times(5)).fetch(any());
    }

    @Test
    @DisplayName("rodada de faltantes consulta só os tickers sem cotação")
    void refreshMissingFetchesOnlyMissing() {
        when(quotes.findMissingTickers()).thenReturn(List.of("ITUB4"));
        when(provider.fetch(B3Ticker.of("ITUB4"))).thenReturn(quoted("33.00"));

        refresher.refreshMissing();

        verify(quotes, never()).findHeldTickers();
        verify(provider, times(1)).fetch(any());
        verify(quotes).upsert("ITUB4", new BigDecimal("33.00"), MARKET, NOW);
    }

    @Test
    @DisplayName("sem token nenhuma consulta nem limpeza acontece")
    void disabledWithoutToken() {
        when(provider.isConfigured()).thenReturn(false);

        refresher.refreshHeld();
        refresher.refreshMissing();

        verifyNoInteractions(quotes);
        verify(provider, never()).fetch(any());
    }

    @Test
    @DisplayName("rodada em andamento faz a outra ser pulada, sem requisição simultânea")
    void overlappingRunIsSkipped() {
        when(quotes.findHeldTickers()).thenReturn(List.of("PETR4"));
        when(provider.fetch(B3Ticker.of("PETR4"))).thenAnswer(invocation -> {
            refresher.refreshMissing();
            return quoted("41.20");
        });

        refresher.refreshHeld();

        verify(quotes, never()).findMissingTickers();
        verify(quotes, times(1)).deleteOrphans();
    }

    @Test
    @DisplayName("mais tickers que o limite ainda consulta todos")
    void overBudgetStillFetchesAll() {
        refresher = new StockQuoteRefresher(quotes, provider, Clock.fixed(NOW, ZoneOffset.UTC), 1);
        when(quotes.findHeldTickers()).thenReturn(List.of("PETR4", "VALE3"));
        when(provider.fetch(any())).thenReturn(quoted("10.00"));

        refresher.refreshHeld();

        verify(provider, times(2)).fetch(any());
    }

    @Test
    @DisplayName("exceção do banco numa rodada não escapa para o scheduler e libera a próxima")
    void failureReleasesGuard() {
        when(quotes.deleteOrphans()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db"));

        refresher.refreshHeld();

        doReturn(0).when(quotes).deleteOrphans();
        when(quotes.findMissingTickers()).thenReturn(List.of());
        refresher.refreshMissing();
        verify(quotes).findMissingTickers();
    }

    private static QuoteResult quoted(String price) {
        return new QuoteResult.Quoted(Money.brl(price), MARKET);
    }
}
