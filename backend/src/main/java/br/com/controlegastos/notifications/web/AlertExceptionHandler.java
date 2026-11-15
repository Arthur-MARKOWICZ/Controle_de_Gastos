package br.com.controlegastos.notifications.web;

import br.com.controlegastos.notifications.application.AlertNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Precede o handler genérico de identity, que trataria o 404 como erro interno.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class AlertExceptionHandler {

    @ExceptionHandler(AlertNotFoundException.class)
    ProblemDetail notFound(AlertNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Alerta não encontrado");
        problem.setTitle("Não encontrado");
        return problem;
    }
}
