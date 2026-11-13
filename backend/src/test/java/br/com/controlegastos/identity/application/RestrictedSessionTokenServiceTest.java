package br.com.controlegastos.identity.application;

import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
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

    private final JwtEncoder encoder = mock(JwtEncoder.class);
    private final RestrictedSessionTokenService service = new RestrictedSessionTokenService(
            encoder, Clock.fixed(NOW, ZoneOffset.UTC), "controle-gastos-api", "controle-gastos-clients",
            Duration.ofMinutes(10));

    private JwtClaimsSet issueAndCapture(UUID userId) {
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("t").header("alg", "HS256").claim("sub", "x").build());
        service.issueRecoverySetupToken(userId);
        var captor = org.mockito.ArgumentCaptor.forClass(JwtEncoderParameters.class);
        org.mockito.Mockito.verify(encoder, org.mockito.Mockito.atLeastOnce()).encode(captor.capture());
        return captor.getValue().getClaims();
    }

    @Test
    @DisplayName("token restrito vale a partir do instante de emissão, nunca antes")
    void validoDesdeAEmissao() {
        JwtClaimsSet claims = issueAndCapture(UUID.randomUUID());

        assertThat(claims.getIssuedAt()).isEqualTo(NOW);
        assertThat(claims.getNotBefore()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("cada token restrito tem um jti único")
    void jtiUnico() {
        UUID userId = UUID.randomUUID();
        String first = issueAndCapture(userId).getId();
        String second = issueAndCapture(userId).getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("assina com HS256")
    void assinaComHs256() {
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("t").header("alg", "HS256").claim("sub", "x").build());
        service.issueRecoverySetupToken(UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(JwtEncoderParameters.class);
        org.mockito.Mockito.verify(encoder).encode(captor.capture());
        assertThat(captor.getValue().getJwsHeader().getAlgorithm().getName()).isEqualTo("HS256");
    }

    @Test
    @DisplayName("usuário nulo lança NullPointerException antes de assinar")
    void usuarioNuloFalha() {
        assertThatThrownBy(() -> service.issueRecoverySetupToken(null)).isInstanceOf(NullPointerException.class);
        org.mockito.Mockito.verifyNoInteractions(encoder);
    }

    @Test
    @DisplayName("falha do assinador propaga JwtEncodingException")
    void falhaDoAssinadorPropaga() {
        when(encoder.encode(any())).thenThrow(new JwtEncodingException("chave inválida"));

        assertThatThrownBy(() -> service.issueRecoverySetupToken(UUID.randomUUID()))
                .isInstanceOf(JwtEncodingException.class)
                .hasMessageContaining("chave inválida");
    }

    @Test
    @DisplayName("token restrito nunca carrega sid, então não vale como sessão comum")
    void semSid() {
        assertThat(issueAndCapture(UUID.randomUUID()).getClaims()).doesNotContainKey("sid");
    }
}
