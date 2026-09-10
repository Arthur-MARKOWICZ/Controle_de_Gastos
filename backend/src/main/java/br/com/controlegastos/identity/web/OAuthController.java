package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.LoginMethodsService;
import br.com.controlegastos.identity.application.OAuthCallbackOutcome;
import br.com.controlegastos.identity.application.OAuthLinkFailedException;
import br.com.controlegastos.identity.application.OAuthLoginFailedException;
import br.com.controlegastos.identity.application.OAuthLoginService;
import br.com.controlegastos.identity.application.SessionService;
import br.com.controlegastos.identity.domain.OAuthClientKind;
import br.com.controlegastos.identity.domain.OAuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    private final AuthenticationService authentication;
    private final OAuthLoginService oauthLogin;
    private final LoginMethodsService loginMethods;
    private final String cookieName;
    private final boolean cookieSecure;
    private final Duration refreshIdleLifetime;
    private final String webBaseUrl;
    private final String mobileRedirectUrl;

    public OAuthController(
            AuthenticationService authentication,
            OAuthLoginService oauthLogin,
            LoginMethodsService loginMethods,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure,
            @Value("${app.auth.refresh-idle-lifetime}") Duration refreshIdleLifetime,
            @Value("${app.oauth.web-base-url}") String webBaseUrl,
            @Value("${app.oauth.mobile-redirect-url}") String mobileRedirectUrl
    ) {
        this.authentication = authentication;
        this.oauthLogin = oauthLogin;
        this.loginMethods = loginMethods;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.refreshIdleLifetime = refreshIdleLifetime;
        this.webBaseUrl = webBaseUrl;
        this.mobileRedirectUrl = mobileRedirectUrl;
    }

    @PostMapping("/{provider}/authorize-url")
    ResponseEntity<AuthorizationUrlResponse> authorizeUrl(@PathVariable String provider) {
        UUID linkingUserId = authentication.currentUserIdOrNull();
        String url = oauthLogin.buildAuthorizationUrl(parseProvider(provider), linkingUserId);
        return ResponseEntity.ok(new AuthorizationUrlResponse(url));
    }

    /**
     * Início do login social para cliente nativo.
     *
     * <p>O aplicativo abre esta rota no navegador do sistema (Custom Tabs no
     * Android), nunca uma WebView embutida, conforme a RFC 8252. O backend gera
     * o {@code state} e redireciona para o provider, de modo que o aplicativo
     * nunca lida com {@code client_secret} nem com o código de autorização.
     */
    @GetMapping("/{provider}/start")
    ResponseEntity<Void> start(
            @PathVariable String provider,
            @RequestParam(required = false) String client
    ) {
        OAuthClientKind kind = "mobile".equalsIgnoreCase(client) ? OAuthClientKind.MOBILE : OAuthClientKind.WEB;
        UUID linkingUserId = authentication.currentUserIdOrNull();
        return redirect(oauthLogin.buildAuthorizationUrl(parseProvider(provider), linkingUserId, kind));
    }

    /**
     * Troca o código de handoff pelos tokens da sessão.
     *
     * <p>Diferente do fluxo web, o refresh volta no corpo: o aplicativo nativo
     * não compartilha o cookie do navegador do sistema e guarda o valor no
     * Keystore. Ver ADR-020.
     */
    @PostMapping("/mobile-handoff")
    ResponseEntity<MobileSessionResponse> mobileHandoff(
            @Valid @RequestBody MobileHandoffRequest request,
            HttpServletRequest httpRequest
    ) {
        SessionService.AuthenticatedSession session =
                oauthLogin.redeemMobileHandoff(request.code(), httpRequest.getRemoteAddr());
        // O nome do cookie é configurável e o aplicativo precisa dele para o
        // /auth/refresh seguinte; adivinhá-lo pelo esquema quebraria em silêncio.
        return ResponseEntity.ok(new MobileSessionResponse(
                session.accessToken(), "Bearer", session.expiresIn(), session.refreshToken(), cookieName));
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
            OAuthCallbackOutcome result = oauthLogin.completeCallback(
                    parseProvider(provider), code, state, httpRequest.getRemoteAddr());
            return switch (result) {
                case OAuthCallbackOutcome.Linked linked -> redirect(securityCallbackBuilder()
                        .queryParam("connected", linked.provider().name().toLowerCase(Locale.ROOT))
                        .build().toUriString());
                case OAuthCallbackOutcome.HandedOffToMobile handoff -> redirect(mobileCallbackBuilder()
                        .queryParam("code", handoff.code())
                        .build().toUriString());
                case OAuthCallbackOutcome.LoggedIn loggedIn ->
                        loginRedirect(loggedIn.outcome(), loggedIn.client());
            };
        } catch (OAuthLinkFailedException exception) {
            String location = securityCallbackBuilder().queryParam("connectError", "oauth_failed").build().toUriString();
            return redirect(location);
        } catch (OAuthLoginFailedException exception) {
            String location = webCallbackBuilder().queryParam("error", "oauth_failed").build().toUriString();
            return redirect(location);
        }
    }

    @DeleteMapping("/{provider}")
    ResponseEntity<Void> unlink(@PathVariable String provider) {
        loginMethods.unlink(authentication.currentUserId(), parseProvider(provider));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> loginRedirect(
            AuthenticationService.LoginOutcome outcome, OAuthClientKind client) {
        boolean mobile = client == OAuthClientKind.MOBILE;
        if (outcome.requiresMfa()) {
            // O desafio segue para o cliente, que conclui em /auth/mfa/verify.
            String location = (mobile ? mobileCallbackBuilder() : webCallbackBuilder())
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

    /** App Link verificado do aplicativo, no mesmo domínio do backend. */
    private UriComponentsBuilder mobileCallbackBuilder() {
        return UriComponentsBuilder.fromUriString(mobileRedirectUrl);
    }

    private UriComponentsBuilder securityCallbackBuilder() {
        return UriComponentsBuilder.fromUriString(webBaseUrl).path("/conta/seguranca");
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

    public record MobileHandoffRequest(@NotBlank @Size(max = 512) String code) {
    }

    public record MobileSessionResponse(
            String accessToken, String tokenType, long expiresIn, String refreshToken, String refreshCookieName) {
    }
}
