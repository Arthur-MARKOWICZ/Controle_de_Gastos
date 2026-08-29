package br.com.controlegastos.ledger.web;

import br.com.controlegastos.ledger.application.LedgerEntryNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class LedgerExceptionHandler {

    @ExceptionHandler(LedgerEntryNotFoundException.class)
    ProblemDetail entryNotFound(LedgerEntryNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Lançamento não encontrado");
        p.setTitle("Lançamento não encontrado");
        p.setProperty("code", "LEDGER_ENTRY_NOT_FOUND");
        return p;
    }
}
