package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.OAuthLoginFailedException;
import br.com.controlegastos.identity.application.OAuthLoginService;
import br.com.controlegastos.identity.domain.OAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    private final OAuthLoginService oauthLogin;
    private final String cookieName;
    private final boolean cookieSecure;
    private final Duration refreshIdleLifetime;
    private final String webBaseUrl;

    public OAuthController(
            OAuthLoginService oauthLogin,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure,
            @Value("${app.auth.refresh-idle-lifetime}") Duration refreshIdleLifetime,
            @Value("${app.oauth.web-base-url}") String webBaseUrl
    ) {
        this.oauthLogin = oauthLogin;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.refreshIdleLifetime = refreshIdleLifetime;
        this.webBaseUrl = webBaseUrl;
    }

    @PostMapping("/{provider}/authorize-url")
    ResponseEntity<AuthorizationUrlResponse> authorizeUrl(@PathVariable String provider) {
        String url = oauthLogin.buildAuthorizationUrl(parseProvider(provider));
        return ResponseEntity.ok(new AuthorizationUrlResponse(url));
    }

    @GetMapping("/{provider}/callback")
    ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest httpRequest
    ) {
        try {
            if (code == null || state == null) {
                throw new OAuthLoginFailedException();
            }
            AuthenticationService.LoginOutcome outcome = oauthLogin.completeCallback(
                    parseProvider(provider), code, state, httpRequest.getRemoteAddr());
            if (outcome.requiresMfa()) {
                String location = webCallbackBuilder()
                        .queryParam("mfaRequired", "true")
                        .queryParam("challengeId", outcome.challenge().challengeId())
                        .queryParam("expiresIn", outcome.challenge().expiresIn())
                        .build()
                        .toUriString();
                return redirect(location);
            }
            String location = webCallbackBuilder().queryParam("status", "ok").build().toUriString();
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, location)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(outcome.session().refreshToken()).toString())
                    .build();
        } catch (OAuthLoginFailedException exception) {
            String location = webCallbackBuilder().queryParam("error", "oauth_failed").build().toUriString();
            return redirect(location);
        }
    }

    private OAuthProvider parseProvider(String raw) {
        try {
            return OAuthProvider.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new OAuthLoginFailedException();
        }
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
    }

    private UriComponentsBuilder webCallbackBuilder() {
        return UriComponentsBuilder.fromUriString(webBaseUrl).path("/oauth/callback");
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

    public record AuthorizationUrlResponse(String authorizationUrl) {
    }
}
