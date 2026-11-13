package br.com.controlegastos.identity.config;

import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.SessionService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SessionValidationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryTokenWithAnActiveSessionPassesThrough() throws Exception {
        SessionService sessions = mock(SessionService.class);
        when(sessions.isActive(any(), any())).thenReturn(true);
        SessionValidationFilter filter = new SessionValidationFilter(sessions, new AuthenticationProblemWriter(objectMapper()));
        authenticateWithOrdinaryToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void ordinaryTokenWithARevokedSessionIsUnauthorized() throws Exception {
        SessionService sessions = mock(SessionService.class);
        when(sessions.isActive(any(), any())).thenReturn(false);
        SessionValidationFilter filter = new SessionValidationFilter(sessions, new AuthenticationProblemWriter(objectMapper()));
        authenticateWithOrdinaryToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void restrictedTokenIsAllowedOnEnrollAndConfirmEndpoints() throws Exception {
        SessionService sessions = mock(SessionService.class);
        SessionValidationFilter filter = new SessionValidationFilter(sessions, new AuthenticationProblemWriter(objectMapper()));
        authenticateWithRestrictedToken();

        for (String path : new String[] {"/api/v1/mfa/enroll", "/api/v1/mfa/enroll/confirm"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            assertThat(chain.getRequest()).as("chain invoked for " + path).isNotNull();
        }
    }

    @Test
    void restrictedTokenIsForbiddenOnAnyOtherEndpoint() throws Exception {
        SessionService sessions = mock(SessionService.class);
        SessionValidationFilter filter = new SessionValidationFilter(sessions, new AuthenticationProblemWriter(objectMapper()));
        authenticateWithRestrictedToken();

        for (String[] methodAndPath : new String[][] {
                {"GET", "/api/v1/users/me"},
                {"GET", "/api/v1/ledger/summary"},
                {"POST", "/api/v1/mfa/disable"},
                {"PUT", "/api/v1/income"}
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest(methodAndPath[0], methodAndPath[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            assertThat(chain.getRequest()).as("chain not invoked for " + methodAndPath[1]).isNull();
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    private void authenticateWithOrdinaryToken() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void authenticateWithRestrictedToken() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("mfa_scope", "RECOVERY_SETUP")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private tools.jackson.databind.ObjectMapper objectMapper() {
        return new tools.jackson.databind.ObjectMapper();
    }

    private final SessionService sessionService = mock(SessionService.class);
    private final SessionValidationFilter subject =
            new SessionValidationFilter(sessionService, new AuthenticationProblemWriter(objectMapper()));

    private MockHttpServletResponse run(String method, String path, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        subject.doFilterInternal(new MockHttpServletRequest(method, path), response, chain);
        return response;
    }

    private void authenticate(Jwt.Builder builder) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    @Test
    @DisplayName("requisição anônima segue para a cadeia; a autorização decide depois")
    void anonimaSegue() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        run("GET", "/api/v1/auth/login", chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("token comum sem sid responde 401 AUTHENTICATION_FAILED")
    void semSidResponde401() throws Exception {
        authenticate(Jwt.withTokenValue("t").header("alg", "HS256").subject(UUID.randomUUID().toString()));
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run("GET", "/api/v1/users/me", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_FAILED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("subject que não é UUID responde 401 sem consultar sessões")
    void subjectInvalidoResponde401() throws Exception {
        authenticate(Jwt.withTokenValue("t").header("alg", "HS256").subject("nao-uuid")
                .claim("sid", UUID.randomUUID().toString()));

        assertThat(run("GET", "/api/v1/users/me", new MockFilterChain()).getStatus()).isEqualTo(401);
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("sessão revogada limpa o contexto de segurança")
    void sessaoRevogadaLimpaContexto() throws Exception {
        when(sessionService.isActive(any(), any())).thenReturn(false);
        authenticateWithOrdinaryToken();

        run("GET", "/api/v1/users/me", new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("token restrito em GET no endpoint de cadastro responde 403 RESTRICTED_SESSION")
    void restritoEmGetResponde403() throws Exception {
        authenticateWithRestrictedToken();

        MockHttpServletResponse response = run("GET", "/api/v1/mfa/enroll", new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("RESTRICTED_SESSION");
    }

    @Test
    @DisplayName("token restrito com barra final no caminho não passa pela lista permitida")
    void restritoComBarraFinalResponde403() throws Exception {
        authenticateWithRestrictedToken();

        assertThat(run("POST", "/api/v1/mfa/enroll/", new MockFilterChain()).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("token restrito não consulta sessões, pois não tem sid")
    void restritoNaoConsultaSessoes() throws Exception {
        authenticateWithRestrictedToken();

        run("POST", "/api/v1/mfa/enroll", new MockFilterChain());

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("sid malformado responde 401")
    void sidMalformadoResponde401() throws Exception {
        authenticate(Jwt.withTokenValue("t").header("alg", "HS256").subject(UUID.randomUUID().toString())
                .claim("sid", "nao-uuid"));

        assertThat(run("GET", "/api/v1/users/me", new MockFilterChain()).getStatus()).isEqualTo(401);
    }
}
