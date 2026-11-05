package br.com.controlegastos.sharing.web;

import br.com.controlegastos.sharing.application.SharingInviteException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class SharingExceptionHandler {

    @ExceptionHandler(SharingInviteException.class)
    ProblemDetail inviteFailed(SharingInviteException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Convite não realizado");
        problem.setProperty("code", "sharing.invite_failed");
        return problem;
    }
}
