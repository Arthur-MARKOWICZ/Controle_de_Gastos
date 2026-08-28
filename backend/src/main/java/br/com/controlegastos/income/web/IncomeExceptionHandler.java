package br.com.controlegastos.income.web;

import br.com.controlegastos.income.domain.IncomeBelowAllocationsException;
import br.com.controlegastos.income.domain.IncomeNotConfiguredException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IncomeController.class)
class IncomeExceptionHandler {

    @ExceptionHandler(IncomeNotConfiguredException.class)
    ProblemDetail notConfigured(IncomeNotConfiguredException exception) {
        ProblemDetail problem = problem(
                HttpStatus.NOT_FOUND,
                "INCOME_NOT_CONFIGURED",
                "Renda não configurada",
                exception.getMessage());
        problem.setProperty("month", exception.month().toString());
        return problem;
    }

    @ExceptionHandler(IncomeBelowAllocationsException.class)
    ProblemDetail belowAllocations(IncomeBelowAllocationsException exception) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "INCOME_BELOW_BASE_ALLOCATIONS",
                "Renda menor que as verbas-base",
                exception.getMessage());
        problem.setProperty("requiredMinimum", exception.requiredMinimum().toPlainString());
        problem.setProperty("shortfall", exception.shortfall().toPlainString());
        problem.setProperty("currency", exception.requiredMinimum().currency());
        return problem;
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail concurrentChange() {
        return problem(
                HttpStatus.CONFLICT,
                "INCOME_CONCURRENT_CHANGE",
                "Alteração concorrente",
                "A renda foi alterada em outra requisição. Recarregue os dados e tente novamente.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
