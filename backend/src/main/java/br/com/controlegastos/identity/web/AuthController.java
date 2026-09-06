package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.LoginMethodsService;
import br.com.controlegastos.identity.application.MfaLoginService;
import br.com.controlegastos.identity.application.RecoveryLoginService;
import br.com.controlegastos.identity.application.RestrictedSessionTokenService;
import br.com.controlegastos.identity.application.SessionService;
import br.com.controlegastos.identity.application.PasswordResetService;
import br.com.controlegastos.identity.domain.OAuthProvider;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authentication;
    private final MfaLoginService mfaLogin;
    private final RecoveryLoginService recoveryLogin;
    private final LoginMethodsService loginMethods;
    private final String cookieName;
    private final boolean cookieSecure;
    private final Duration refreshIdleLifetime;
    private final PasswordResetService passwordResets;

    public AuthController(
            AuthenticationService authentication,
            MfaLoginService mfaLogin,
            RecoveryLoginService recoveryLogin,
            LoginMethodsService loginMethods,
            PasswordResetService passwordResets,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure,
            @Value("${app.auth.refresh-idle-lifetime}") Duration refreshIdleLifetime
    ) {
        this.authentication = authentication;
        this.mfaLogin = mfaLogin;
        this.recoveryLogin = recoveryLogin;
        this.loginMethods = loginMethods;
        this.passwordResets = passwordResets;
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
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthenticationService.LoginOutcome outcome =
                authentication.login(request.email(), request.password(), httpRequest.getRemoteAddr());
        if (outcome.requiresMfa()) {
            return ResponseEntity.ok(new MfaChallengeResponse(
                    true, outcome.challenge().challengeId(), outcome.challenge().expiresIn()));
        }
        return tokenResponse(outcome.session());
    }

    @PostMapping("/mfa/verify")
    ResponseEntity<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        return tokenResponse(mfaLogin.verify(request.challengeId(), request.code(), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/mfa/recovery")
    RestrictedTokenResponse verifyRecoveryCode(@Valid @RequestBody MfaRecoveryRequest request, HttpServletRequest httpRequest) {
        RestrictedSessionTokenService.RestrictedToken token = recoveryLogin.verify(
                request.challengeId(), request.recoveryCode(), httpRequest.getRemoteAddr());
        return new RestrictedTokenResponse(token.token(), "Bearer", token.expiresIn());
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

    @PostMapping("/password-reset-requests")
    ResponseEntity<Map<String, String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                               HttpServletRequest httpRequest) {
        passwordResets.request(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", "Se houver uma conta para este endereço, enviaremos as instruções."
        ));
    }

    @PostMapping("/password-resets")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmation request) {
        passwordResets.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, deleteRefreshCookie().toString()).build();
    }

    @PostMapping("/password")
    ResponseEntity<Void> addPassword(@Valid @RequestBody AddPasswordRequest request) {
        loginMethods.addPassword(authentication.currentUserId(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login-methods")
    LoginMethodsResponse loginMethodsStatus() {
        LoginMethodsService.LoginMethods status = loginMethods.status(authentication.currentUserId());
        return new LoginMethodsResponse(status.hasPassword(), status.linkedProviders());
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

    public record MfaVerifyRequest(
            @NotBlank @Size(max = 512) String challengeId,
            @NotBlank @Size(max = 10) String code
    ) {
    }

    public record MfaChallengeResponse(boolean mfaRequired, String challengeId, long expiresIn) {
    }

    public record MfaRecoveryRequest(
            @NotBlank @Size(max = 512) String challengeId,
            @NotBlank @Size(max = 32) String recoveryCode
    ) {
    }

    public record RestrictedTokenResponse(String restrictedToken, String tokenType, long expiresIn) {
    }

    public record PasswordResetRequest(@NotNull String email) { }
    public record PasswordResetConfirmation(@NotBlank @Size(max = 256) String token,
                                            @NotNull @Size(min = 12, max = 128) String newPassword) { }

    public record AddPasswordRequest(@NotNull @Size(min = 12, max = 128) String password) { }
    public record LoginMethodsResponse(boolean hasPassword, List<OAuthProvider> linkedProviders) { }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
