package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.controlegastos.identity.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticationServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTheAuthenticatedUserAndSessionFromTheJwt() {
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID sessionId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        AuthenticationService service = new AuthenticationService(
                Mockito.mock(UserAccountRepository.class),
                Mockito.mock(SessionService.class),
                Mockito.mock(AuthAttemptService.class),
                Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                Clock.systemUTC()
        );

        assertThat(service.currentUserId()).isEqualTo(userId);
        assertThat(service.currentSessionId()).isEqualTo(sessionId);
    }
}
