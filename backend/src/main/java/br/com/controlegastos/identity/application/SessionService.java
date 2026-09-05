package br.com.controlegastos.identity.application;

import br.com.controlegastos.identity.domain.AuthSession;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private final SessionRepository sessions;
    private final RefreshTokenCodec refreshTokens;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenLifetime;
    private final Duration refreshIdleLifetime;
    private final Duration sessionAbsoluteLifetime;
    private final Clock clock;

    public SessionService(
            SessionRepository sessions,
            RefreshTokenCodec refreshTokens,
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${app.auth.issuer}") String issuer,
            @Value("${app.auth.audience}") String audience,
            @Value("${app.auth.access-token-lifetime}") Duration accessTokenLifetime,
            @Value("${app.auth.refresh-idle-lifetime}") Duration refreshIdleLifetime,
            @Value("${app.auth.session-absolute-lifetime}") Duration sessionAbsoluteLifetime
    ) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenLifetime = accessTokenLifetime;
        this.refreshIdleLifetime = refreshIdleLifetime;
        this.sessionAbsoluteLifetime = sessionAbsoluteLifetime;
    }

    @Transactional
    public AuthenticatedSession start(UUID userId) {
        Instant now = clock.instant();
        AuthSession session = AuthSession.start(
                userId,
                "pending",
                now,
                refreshIdleLifetime,
                sessionAbsoluteLifetime
        );
        RefreshTokenCodec.IssuedRefreshToken refresh = refreshTokens.issue(session.id());
        session.rotate(refresh.hash(), now, refreshIdleLifetime);
        sessions.save(session);
        return authenticated(session, refresh.rawValue(), now);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthenticatedSession refresh(String rawRefreshToken) {
        RefreshTokenCodec.ParsedRefreshToken parsed = refreshTokens.parse(rawRefreshToken);
        AuthSession session = sessions.findLockedById(parsed.sessionId())
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (!session.isActiveAt(now)) {
            session.revoke(now, "EXPIRED");
            throw new InvalidRefreshTokenException();
        }
        if (!session.matchesRefreshHash(parsed.hash())) {
            session.revoke(now, "REFRESH_REUSE");
            throw new InvalidRefreshTokenException();
        }
        RefreshTokenCodec.IssuedRefreshToken nextRefresh = refreshTokens.issue(session.id());
        session.rotate(nextRefresh.hash(), now, refreshIdleLifetime);
        return authenticated(session, nextRefresh.rawValue(), now);
    }

    @Transactional
    public void logout(UUID sessionId) {
        sessions.findLockedById(sessionId)
                .ifPresent(session -> session.revoke(clock.instant(), "LOGOUT"));
    }

    @Transactional
    public void revokeAllForPasswordReset(UUID userId) {
        sessions.revokeActiveByUserId(userId, clock.instant(), "PASSWORD_RESET");
    }

    @Transactional
    public void revokeAllForMfaChange(UUID userId) {
        sessions.revokeActiveByUserId(userId, clock.instant(), "MFA_CHANGE");
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, UUID userId) {
        return sessions.findById(sessionId)
                .filter(session -> session.userId().equals(userId))
                .map(session -> session.isActiveAt(clock.instant()))
                .orElse(false);
    }

    private AuthenticatedSession authenticated(AuthSession session, String refreshToken, Instant now) {
        return new AuthenticatedSession(
                issueAccessToken(session.userId(), session.id(), now),
                accessTokenLifetime.toSeconds(),
                refreshToken
        );
    }

    private String issueAccessToken(UUID userId, UUID sessionId, Instant issuedAt) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(issuedAt.plus(accessTokenLifetime))
                .claim("sid", sessionId.toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record AuthenticatedSession(String accessToken, long expiresIn, String refreshToken) {
    }
}
