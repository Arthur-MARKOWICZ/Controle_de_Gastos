package br.com.controlegastos.envelopes.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = EnvelopeController.class)
class EnvelopeConcurrencyExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange() {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A verba foi alterada em outra requisição. Recarregue os dados e tente novamente.");
        p.setTitle("Alteração concorrente");
        p.setProperty("code", "ENVELOPE_CONCURRENT_CHANGE");
        return p;
    }
}
