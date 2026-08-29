package br.com.controlegastos.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigurationTest {

    @Test
    void permitsDeleteInTheCorsPreflightForHistoryEntries() {
        var source = new SecurityConfiguration().corsConfigurationSource(List.of("http://localhost:3000"));
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/ledger/entries/entry-id");
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "DELETE");

        assertThat(source.getCorsConfiguration(request).getAllowedMethods()).contains("DELETE");
    }
}
