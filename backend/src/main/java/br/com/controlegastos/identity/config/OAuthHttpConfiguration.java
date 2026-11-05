package br.com.controlegastos.identity.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class OAuthHttpConfiguration {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    RestClient oauthRestClient() {
        return RestClient.builder()
                .requestFactory(oauthClientHttpRequestFactory())
                .build();
    }

    JdkClientHttpRequestFactory oauthClientHttpRequestFactory() {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }
}
