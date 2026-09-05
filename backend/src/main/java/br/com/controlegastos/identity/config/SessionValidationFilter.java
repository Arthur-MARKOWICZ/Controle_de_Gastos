package br.com.controlegastos.identity.config;

import br.com.controlegastos.identity.application.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class SessionValidationFilter extends OncePerRequestFilter {

    private static final String MFA_SCOPE_CLAIM = "mfa_scope";

    private final SessionService sessions;
    private final AuthenticationProblemWriter problems;

    SessionValidationFilter(SessionService sessions, AuthenticationProblemWriter problems) {
        this.sessions = sessions;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String mfaScope = jwtAuthentication.getToken().getClaimAsString(MFA_SCOPE_CLAIM);
            if (mfaScope != null) {
                if (!isMfaSetupRequest(request)) {
                    forbidden(response);
                    return;
                }
            } else {
                try {
                    UUID userId = UUID.fromString(jwtAuthentication.getToken().getSubject());
                    UUID sessionId = UUID.fromString(jwtAuthentication.getToken().getClaimAsString("sid"));
                    if (!sessions.isActive(sessionId, userId)) {
                        unauthorized(response);
                        return;
                    }
                } catch (RuntimeException exception) {
                    unauthorized(response);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isMfaSetupRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/v1/mfa/enroll".equals(path) || "/api/v1/mfa/enroll/confirm".equals(path);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        problems.write(response);
    }

    private void forbidden(HttpServletResponse response) throws IOException {
        problems.writeForbidden(response);
    }
}
