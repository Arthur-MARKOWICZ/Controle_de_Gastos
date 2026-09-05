package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class RestrictedSessionTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void issuesATokenWithMfaScopeAndNoSessionIdClaim() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        Jwt encoded = Jwt.withTokenValue("encoded-token")
                .header("alg", "HS256")
                .claim("sub", "irrelevant")
                .build();
        when(encoder.encode(any())).thenReturn(encoded);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RestrictedSessionTokenService service = new RestrictedSessionTokenService(
                encoder, clock, "controle-gastos-api", "controle-gastos-clients", Duration.ofMinutes(10));
        UUID userId = UUID.randomUUID();

        RestrictedSessionTokenService.RestrictedToken token = service.issueRecoverySetupToken(userId);

        assertThat(token.token()).isEqualTo("encoded-token");
        assertThat(token.expiresIn()).isEqualTo(600);

        var captor = org.mockito.ArgumentCaptor.forClass(JwtEncoderParameters.class);
        org.mockito.Mockito.verify(encoder).encode(captor.capture());
        var claims = captor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getClaimAsString("mfa_scope")).isEqualTo("RECOVERY_SETUP");
        assertThat(claims.getClaimAsString("sid")).isNull();
        assertThat(claims.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(claims.getClaimAsString("iss")).contains("controle-gastos-api");
        assertThat(claims.getClaimAsStringList("aud")).containsExactly("controle-gastos-clients");
    }
}
