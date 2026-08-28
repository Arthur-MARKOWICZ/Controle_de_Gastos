package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.SessionService;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authentication;
    private final String cookieName;
    private final boolean cookieSecure;
    private final Duration refreshIdleLifetime;

    public AuthController(
            AuthenticationService authentication,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure,
            @Value("${app.auth.refresh-idle-lifetime}") Duration refreshIdleLifetime
    ) {
        this.authentication = authentication;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.refreshIdleLifetime = refreshIdleLifetime;
    }

    @PostMapping("/register")
    ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest httpRequest) {
        authentication.register(request.email(), request.password(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", "Se o cadastro puder ser concluído, você já poderá entrar."
        ));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return tokenResponse(authentication.login(request.email(), request.password(), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        return tokenResponse(authentication.refresh(refreshTokenFrom(request)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        authentication.logout();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(SessionService.AuthenticatedSession session) {
        TokenResponse body = new TokenResponse(session.accessToken(), "Bearer", session.expiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(refreshIdleLifetime)
                .build();
    }

    private ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String refreshTokenFrom(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 12, max = 128) String password
    ) {
    }

    public record LoginRequest(
            @NotNull String email,
            @NotNull String password
    ) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
