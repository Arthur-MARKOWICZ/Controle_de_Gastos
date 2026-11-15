package br.com.controlegastos.ledger.web;

import br.com.controlegastos.ledger.application.RecurringNotFoundException;
import br.com.controlegastos.ledger.domain.OccurrenceNotPendingException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {RecurringEntryController.class, RecurringOccurrenceController.class})
class RecurringExceptionHandler {

    @ExceptionHandler(RecurringNotFoundException.class)
    ProblemDetail notFound() {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recorrência não encontrada");
        p.setTitle("Recorrência não encontrada");
        p.setProperty("code", "RECURRING_NOT_FOUND");
        return p;
    }

    @ExceptionHandler({OccurrenceNotPendingException.class, OptimisticLockingFailureException.class})
    ProblemDetail alreadyResolved() {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A pendência já foi confirmada ou descartada. Recarregue a lista.");
        p.setTitle("Pendência já resolvida");
        p.setProperty("code", "RECURRING_OCCURRENCE_RESOLVED");
        return p;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Operação inválida");
        p.setProperty("code", "RECURRING_INVALID_STATE");
        return p;
    }
}
