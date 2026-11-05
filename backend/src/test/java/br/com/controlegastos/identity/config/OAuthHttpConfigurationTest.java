package br.com.controlegastos.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OAuthHttpConfigurationTest {

    @Test
    void configuresConnectAndReadTimeoutsForOAuthRequests() {
        var requestFactory = new OAuthHttpConfiguration().oauthClientHttpRequestFactory();
        var httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");

        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(3));
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
                .isEqualTo(Duration.ofSeconds(5));
    }
}
