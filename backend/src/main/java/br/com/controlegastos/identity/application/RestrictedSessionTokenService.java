package br.com.controlegastos.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class RestrictedSessionTokenService {

    public static final String RECOVERY_SETUP_SCOPE = "RECOVERY_SETUP";
    private static final String MFA_SCOPE_CLAIM = "mfa_scope";

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;

    public RestrictedSessionTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${app.auth.issuer}") String issuer,
            @Value("${app.auth.audience}") String audience,
            @Value("${app.auth.mfa.recovery-session-lifetime}") Duration lifetime
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.lifetime = lifetime;
    }

    public RestrictedToken issueRecoverySetupToken(UUID userId) {
        Instant now = clock.instant();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(lifetime))
                .claim(MFA_SCOPE_CLAIM, RECOVERY_SETUP_SCOPE)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new RestrictedToken(token, lifetime.toSeconds());
    }

    public record RestrictedToken(String token, long expiresIn) {
    }
}
