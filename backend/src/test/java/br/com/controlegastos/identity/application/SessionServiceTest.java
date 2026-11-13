package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.domain.AuthSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class SessionServiceTest {

    private static final Instant START = Instant.parse("2026-09-15T15:00:00Z");
    private static final Duration ACCESS = Duration.ofMinutes(15);
    private static final Duration IDLE = Duration.ofDays(7);
    private static final Duration ABSOLUTE = Duration.ofDays(30);

    private final SessionRepository repository = mock(SessionRepository.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final MutableClock clock = new MutableClock(START);
    private final Map<UUID, AuthSession> stored = new HashMap<>();
    private SessionService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SessionService(repository, new RefreshTokenCodec(), jwtEncoder, clock,
                "controle-gastos", "controle-gastos-api", ACCESS, IDLE, ABSOLUTE);
        when(jwtEncoder.encode(any())).thenReturn(
                Jwt.withTokenValue("jwt-de-teste").header("alg", "HS256").claim("sub", userId.toString()).build());
        when(repository.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession session = invocation.getArgument(0);
            stored.put(session.id(), session);
            return session;
        });
        when(repository.findLockedById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
    }

    private UUID sessionIdOf(String refresh) {
        return UUID.fromString(refresh.substring(0, refresh.indexOf('.')));
    }

    @Test
    @DisplayName("inicia sessão com access token assinado, sid e expiração do access")
    void iniciaSessao() {
        SessionService.AuthenticatedSession session = service.start(userId);

        assertThat(session.accessToken()).isEqualTo("jwt-de-teste");
        assertThat(session.expiresIn()).isEqualTo(900);
        ArgumentCaptor<JwtEncoderParameters> params = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(params.capture());
        assertThat(params.getValue().getClaims().getSubject()).isEqualTo(userId.toString());
        assertThat(params.getValue().getClaims().<String>getClaim("sid")).isEqualTo(sessionIdOf(session.refreshToken()).toString());
        assertThat(params.getValue().getClaims().getExpiresAt()).isEqualTo(START.plus(ACCESS));
        assertThat(service.isActive(sessionIdOf(session.refreshToken()), userId)).isTrue();
    }

    @Test
    @DisplayName("refresh rotaciona o token: o novo funciona e o antigo deixa de valer")
    void refreshRotaciona() {
        String first = service.start(userId).refreshToken();
        clock.advance(Duration.ofMinutes(20));

        String second = service.refresh(first).refreshToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(sessionIdOf(second)).isEqualTo(sessionIdOf(first));
    }

    @Test
    @DisplayName("reutilizar refresh antigo revoga a sessão inteira (REFRESH_REUSE)")
    void reutilizarRefreshRevogaSessao() {
        String first = service.start(userId).refreshToken();
        String second = service.refresh(first).refreshToken();

        assertThatThrownBy(() -> service.refresh(first)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.refresh(second)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(service.isActive(sessionIdOf(first), userId)).isFalse();
    }

    @Test
    @DisplayName("refresh após o prazo ocioso revoga e lança InvalidRefreshTokenException")
    void refreshAposPrazoOciosoFalha() {
        String refresh = service.start(userId).refreshToken();
        clock.advance(IDLE.plusSeconds(1));

        assertThatThrownBy(() -> service.refresh(refresh)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(stored.get(sessionIdOf(refresh)).revokedAt()).isNotNull();
    }

    @Test
    @DisplayName("uso contínuo não estende a sessão além do prazo absoluto")
    void prazoAbsolutoLimitaSessao() {
        String refresh = service.start(userId).refreshToken();
        for (int day = 0; day < 29; day++) {
            clock.advance(Duration.ofDays(1));
            refresh = service.refresh(refresh).refreshToken();
        }
        clock.advance(Duration.ofDays(1));

        String last = refresh;
        assertThatThrownBy(() -> service.refresh(last)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("sessão encerrada no logout não aceita refresh")
    void logoutImpedeRefresh() {
        String refresh = service.start(userId).refreshToken();

        service.logout(sessionIdOf(refresh));

        assertThatThrownBy(() -> service.refresh(refresh)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh malformado lança InvalidRefreshTokenException")
    void refreshMalformadoFalha() {
        assertThatThrownBy(() -> service.refresh("sem-ponto")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh de sessão inexistente lança InvalidRefreshTokenException")
    void refreshDeSessaoInexistenteFalha() {
        String forged = UUID.randomUUID() + "." + "a".repeat(43);

        assertThatThrownBy(() -> service.refresh(forged)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("sessão de outro usuário não é considerada ativa")
    void sessaoDeOutroUsuarioNaoEAtiva() {
        UUID sessionId = sessionIdOf(service.start(userId).refreshToken());

        assertThat(service.isActive(sessionId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("revogações em massa registram o motivo de cada fluxo")
    void revogacoesEmMassaComMotivo() {
        service.revokeAllForPasswordReset(userId);
        service.revokeAllForMfaChange(userId);
        service.revokeAllForAccountClosure(userId);

        verify(repository).revokeActiveByUserId(userId, START, "PASSWORD_RESET");
        verify(repository).revokeActiveByUserId(userId, START, "MFA_CHANGE");
        verify(repository).revokeActiveByUserId(userId, START, "ACCOUNT_CLOSED");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
