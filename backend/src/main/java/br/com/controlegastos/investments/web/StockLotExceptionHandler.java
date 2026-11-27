package br.com.controlegastos.investments.web;

import br.com.controlegastos.investments.application.StockLotNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StockLotController.class)
class StockLotExceptionHandler {

    @ExceptionHandler(StockLotNotFoundException.class)
    ProblemDetail notFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Lote não encontrado");
        problem.setTitle("Lote não encontrado");
        problem.setProperty("code", "STOCK_LOT_NOT_FOUND");
        return problem;
    }
}
