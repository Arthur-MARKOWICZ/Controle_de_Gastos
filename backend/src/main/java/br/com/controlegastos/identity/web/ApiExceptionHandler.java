package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.InvalidCredentialsException;
import br.com.controlegastos.identity.application.InvalidMfaChallengeException;
import br.com.controlegastos.identity.application.InvalidPasswordConfirmationException;
import br.com.controlegastos.identity.application.InvalidRecoveryCodeException;
import br.com.controlegastos.identity.application.InvalidRefreshTokenException;
import br.com.controlegastos.identity.application.InvalidPasswordResetTokenException;
import br.com.controlegastos.identity.application.MfaAlreadyEnabledException;
import br.com.controlegastos.identity.application.MfaNotEnabledException;
import br.com.controlegastos.identity.application.OAuthLoginFailedException;
import br.com.controlegastos.identity.application.TooManyAttemptsException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String AUTHENTICATION_DETAIL =
            "Não foi possível autenticar com os dados informados.";
    private final String cookieName;
    private final boolean cookieSecure;

    ApiExceptionHandler(
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure
    ) {
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail authenticationFailed() {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Falha de autenticação", AUTHENTICATION_DETAIL);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ProblemDetail> refreshFailed() {
        ResponseCookie deleted = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, deleted.toString())
                .body(problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                        "Falha de autenticação", AUTHENTICATION_DETAIL));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ProblemDetail tooManyAttempts() {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                "Muitas tentativas", "Aguarde alguns minutos antes de tentar novamente.");
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    ProblemDetail invalidPasswordResetToken() {
        return problem(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID",
                "Link indisponível", "O link de redefinição é inválido ou expirou.");
    }

    @ExceptionHandler(InvalidMfaChallengeException.class)
    ProblemDetail invalidMfaChallenge() {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Falha de autenticação", AUTHENTICATION_DETAIL);
    }

    @ExceptionHandler(InvalidRecoveryCodeException.class)
    ProblemDetail invalidRecoveryCode() {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Falha de autenticação", AUTHENTICATION_DETAIL);
    }

    @ExceptionHandler(InvalidPasswordConfirmationException.class)
    ProblemDetail invalidPasswordConfirmation() {
        return problem(HttpStatus.UNAUTHORIZED, "PASSWORD_REQUIRED_INVALID",
                "Senha incorreta", "Senha atual incorreta.");
    }

    @ExceptionHandler(MfaAlreadyEnabledException.class)
    ProblemDetail mfaAlreadyEnabled() {
        return problem(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED",
                "MFA já ativo", "A autenticação em duas etapas já está ativa para esta conta.");
    }

    @ExceptionHandler(MfaNotEnabledException.class)
    ProblemDetail mfaNotEnabled() {
        return problem(HttpStatus.CONFLICT, "MFA_NOT_ENABLED",
                "MFA não ativo", "A autenticação em duas etapas não está ativa para esta conta.");
    }

    @ExceptionHandler(OAuthLoginFailedException.class)
    ProblemDetail oauthLoginFailed() {
        return problem(HttpStatus.BAD_REQUEST, "OAUTH_LOGIN_FAILED",
                "Falha no login social", "Não foi possível concluir a autenticação com o provedor.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Requisição inválida", "Revise os dados informados.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internalError(Exception exception) {
        LOG.error("Erro não tratado ao processar a requisição", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno", "Não foi possível concluir a operação.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
