package br.com.controlegastos.envelopes.web;

import br.com.controlegastos.envelopes.application.EnvelopeForbiddenException;
import br.com.controlegastos.envelopes.application.EnvelopeNotFoundException;
import br.com.controlegastos.envelopes.domain.AllocationExceedsIncomeException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class EnvelopeExceptionHandler {

    @ExceptionHandler(EnvelopeNotFoundException.class)
    ProblemDetail notFound(EnvelopeNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Verba não encontrada");
        p.setProperty("code", "ENVELOPE_NOT_FOUND");
        return p;
    }

    @ExceptionHandler(EnvelopeForbiddenException.class)
    ProblemDetail forbidden(EnvelopeForbiddenException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        p.setTitle("Operação não permitida");
        p.setProperty("code", "FORBIDDEN");
        return p;
    }

    @ExceptionHandler(AllocationExceedsIncomeException.class)
    ProblemDetail allocation(AllocationExceedsIncomeException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Renda menor que as verbas-base");
        p.setProperty("code", "ALLOCATION_EXCEEDS_INCOME");
        p.setProperty("excess", ex.excess().toPlainString());
        p.setProperty("currency", ex.excess().currency());
        return p;
    }
}
