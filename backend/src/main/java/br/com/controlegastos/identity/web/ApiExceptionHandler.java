package br.com.controlegastos.identity.web;

import br.com.controlegastos.identity.application.InvalidCredentialsException;
import br.com.controlegastos.identity.application.InvalidRefreshTokenException;
import br.com.controlegastos.identity.application.InvalidPasswordResetTokenException;
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

@RestControllerAdvice
class ApiExceptionHandler {

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

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Requisição inválida", "Revise os dados informados.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internalError() {
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
