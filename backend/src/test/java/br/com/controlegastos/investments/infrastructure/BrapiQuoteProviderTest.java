package br.com.controlegastos.investments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.controlegastos.investments.application.QuoteResult;
import br.com.controlegastos.investments.domain.B3Ticker;
import java.net.SocketTimeoutException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Mapeamento da resposta da brapi.dev para {@link QuoteResult} (ADR-0035, design D4). */
class BrapiQuoteProviderTest {

    private static final String URL = "https://brapi.example/api/quote/PETR4";
    private static final B3Ticker PETR4 = B3Ticker.of("PETR4");

    private MockRestServiceServer server;
    private BrapiQuoteProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new BrapiQuoteProvider(builder.build(), "https://brapi.example/", "segredo-do-token");
    }

    @Test
    @DisplayName("envia o token só no header Bearer e lê preço e instante de mercado")
    void readsPriceAndMarketTime() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer segredo-do-token"))
                .andRespond(withSuccess(body("41.20", "\"2026-11-26T20:00:00.000Z\""), MediaType.APPLICATION_JSON));

        QuoteResult result = provider.fetch(PETR4);

        assertThat(result).isInstanceOf(QuoteResult.Quoted.class);
        QuoteResult.Quoted quoted = (QuoteResult.Quoted) result;
        assertThat(quoted.price().toPlainString()).isEqualTo("41.20");
        assertThat(quoted.quotedAt()).isEqualTo(Instant.parse("2026-11-26T20:00:00Z"));
        server.verify();
    }

    @ParameterizedTest(name = "{0} vira {1}")
    @CsvSource({"38.470001, 38.47", "10.125, 10.12", "10.135, 10.14", "41, 41.00", "0.015, 0.02"})
    @DisplayName("preço vira BigDecimal do texto e é arredondado a 2 casas com HALF_EVEN")
    void roundsHalfEven(String raw, String expected) {
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(body(raw, "\"2026-11-26T20:00:00Z\""), MediaType.APPLICATION_JSON));

        QuoteResult.Quoted quoted = (QuoteResult.Quoted) provider.fetch(PETR4);

        assertThat(quoted.price().toPlainString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"results\":[]}",
            "{}",
            "{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketTime\":\"2026-11-26T20:00:00Z\"}]}",
            "{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketPrice\":null,\"regularMarketTime\":\"2026-11-26T20:00:00Z\"}]}",
            "{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketPrice\":0,\"regularMarketTime\":\"2026-11-26T20:00:00Z\"}]}",
            "{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketPrice\":-1.5,\"regularMarketTime\":\"2026-11-26T20:00:00Z\"}]}",
            "{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketPrice\":0.004,\"regularMarketTime\":\"2026-11-26T20:00:00Z\"}]}"
    })
    @DisplayName("sem resultado ou com preço ausente, zero ou negativo vira NotFound")
    void missingOrNonPositivePriceIsNotFound(String json) {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.NotFound());
    }

    @Test
    @DisplayName("sem regularMarketTime válido vira Unavailable bad_payload")
    void missingMarketTimeIsBadPayload() {
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(body("41.20", "null"), MediaType.APPLICATION_JSON));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.Unavailable("bad_payload"));
    }

    @Test
    @DisplayName("JSON inválido vira Unavailable bad_payload")
    void invalidJsonIsBadPayload() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.Unavailable("bad_payload"));
    }

    @Test
    @DisplayName("404 vira NotFound")
    void notFound() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.NotFound());
    }

    @Test
    @DisplayName("429 vira RateLimited")
    void rateLimited() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.RateLimited());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    @DisplayName("401 e 403 viram Rejected")
    void rejected(int status) {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.Rejected(status));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500, 503})
    @DisplayName("400 e 5xx viram Unavailable com o status")
    void unavailableStatus(int status) {
        server.expect(once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{\"message\":\"segredo-do-token\"}"));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.Unavailable("http_" + status));
    }

    @Test
    @DisplayName("timeout vira Unavailable io")
    void timeout() {
        server.expect(once(), requestTo(URL)).andRespond(withException(new SocketTimeoutException("lento")));
        assertThat(provider.fetch(PETR4)).isEqualTo(new QuoteResult.Unavailable("io"));
    }

    @Test
    @DisplayName("token em branco desliga o provedor")
    void blankTokenIsNotConfigured() {
        assertThat(provider.isConfigured()).isTrue();
        assertThat(new BrapiQuoteProvider(RestClient.create(), "https://brapi.example", " ").isConfigured()).isFalse();
        assertThat(new BrapiQuoteProvider(RestClient.create(), "https://brapi.example", null).isConfigured()).isFalse();
    }

    private static String body(String price, String time) {
        return "{\"results\":[{\"symbol\":\"PETR4\",\"currency\":\"BRL\",\"regularMarketPrice\":" + price
                + ",\"regularMarketTime\":" + time + "}],\"requestedAt\":\"2026-11-26T20:01:00Z\"}";
    }
}
