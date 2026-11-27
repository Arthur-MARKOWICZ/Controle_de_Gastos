package br.com.controlegastos.investments.web;

import br.com.controlegastos.investments.application.CdbPositionNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CdbPositionController.class)
class CdbPositionExceptionHandler {

    @ExceptionHandler(CdbPositionNotFoundException.class)
    ProblemDetail notFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Posição de CDB não encontrada");
        problem.setTitle("Posição de CDB não encontrada");
        problem.setProperty("code", "CDB_POSITION_NOT_FOUND");
        return problem;
    }
}
