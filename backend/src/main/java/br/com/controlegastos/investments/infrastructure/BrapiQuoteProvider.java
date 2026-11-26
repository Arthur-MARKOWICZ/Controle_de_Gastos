package br.com.controlegastos.investments.infrastructure;

import br.com.controlegastos.investments.application.QuoteProvider;
import br.com.controlegastos.investments.application.QuoteResult;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador da brapi.dev (ADR-0035). O token vai só no header Bearer. O preço é
 * lido como BigDecimal do texto JSON e arredondado a 2 casas com HALF_EVEN, a
 * única exceção de arredondamento na fronteira registrada no ADR-0035.
 * Este adaptador não loga e não propaga o corpo da resposta.
 */
@Component
class BrapiQuoteProvider implements QuoteProvider {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final RestClient restClient;
    private final String baseUrl;
    private final String token;

    @Autowired
    BrapiQuoteProvider(@Value("${app.investments.quotes.brapi-base-url}") String baseUrl,
                       @Value("${app.investments.quotes.brapi-token:}") String token) {
        this(RestClient.builder().requestFactory(requestFactory()).build(), baseUrl, token);
    }

    BrapiQuoteProvider(RestClient restClient, String baseUrl, String token) {
        this.restClient = restClient;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token.strip();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public boolean isConfigured() {
        return !token.isEmpty();
    }

    @Override
    public QuoteResult fetch(B3Ticker ticker) {
        try {
            return restClient.get()
                    .uri(baseUrl + "/api/quote/{ticker}", ticker.value())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 200) {
                            return parse(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
                        }
                        return switch (status) {
                            case 404 -> new QuoteResult.NotFound();
                            case 429 -> new QuoteResult.RateLimited();
                            case 401, 403 -> new QuoteResult.Rejected(status);
                            default -> new QuoteResult.Unavailable("http_" + status);
                        };
                    });
        } catch (RestClientException exception) {
            return new QuoteResult.Unavailable("io");
        }
    }

    private static QuoteResult parse(String body) {
        JsonNode result;
        try {
            result = JSON.readTree(body).path("results").path(0);
        } catch (JacksonException exception) {
            return new QuoteResult.Unavailable("bad_payload");
        }
        if (result.isMissingNode()) return new QuoteResult.NotFound();
        JsonNode priceNode = result.path("regularMarketPrice");
        if (!priceNode.isNumber()) return new QuoteResult.NotFound();
        BigDecimal price = priceNode.decimalValue().setScale(Money.SCALE, RoundingMode.HALF_EVEN);
        if (price.signum() <= 0) return new QuoteResult.NotFound();
        JsonNode timeNode = result.path("regularMarketTime");
        if (!timeNode.isString()) return new QuoteResult.Unavailable("bad_payload");
        try {
            return new QuoteResult.Quoted(Money.brl(price), Instant.parse(timeNode.asString()));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return new QuoteResult.Unavailable("bad_payload");
        }
    }
}
