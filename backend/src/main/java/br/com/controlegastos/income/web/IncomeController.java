package br.com.controlegastos.income.web;

import br.com.controlegastos.income.application.IncomeHistoryEntry;
import br.com.controlegastos.income.application.IncomeHistoryPage;
import br.com.controlegastos.income.application.IncomeService;
import br.com.controlegastos.income.application.IncomeSnapshot;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/income")
class IncomeController {

    private final IncomeService income;

    IncomeController(IncomeService income) {
        this.income = income;
    }

    @PutMapping
    IncomeResponse change(@RequestBody JsonNode request) {
        return IncomeResponse.from(income.change(moneyFrom(request)));
    }

    @GetMapping
    IncomeResponse find(@RequestParam(required = false) String month) {
        IncomeSnapshot snapshot = month == null
                ? income.findCurrent()
                : income.find(parseMonth(month));
        return IncomeResponse.from(snapshot);
    }

    @GetMapping("/history")
    HistoryResponse history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return HistoryResponse.from(income.history(page, size));
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("O mês deve usar o formato YYYY-MM", exception);
        }
    }

    private Money moneyFrom(JsonNode request) {
        if (request == null || !request.isObject() || request.size() != 1 || !request.has("amount")) {
            throw new IllegalArgumentException("Informe somente o campo amount");
        }
        JsonNode amount = request.get("amount");
        if (amount == null || !amount.isString()) {
            throw new IllegalArgumentException("A renda deve ser uma string decimal");
        }
        String decimal = amount.asString();
        if (!decimal.matches("^\\d+(?:\\.\\d{1,2})?$")) {
            throw new IllegalArgumentException("A renda deve ser um decimal positivo com até duas casas");
        }
        return Money.brl(decimal);
    }

    record IncomeResponse(String amount, String currency, String effectiveFrom, Instant changedAt) {
        static IncomeResponse from(IncomeSnapshot snapshot) {
            return new IncomeResponse(
                    snapshot.amount().toPlainString(),
                    snapshot.amount().currency(),
                    snapshot.effectiveFrom().toString(),
                    snapshot.changedAt()
            );
        }
    }

    record HistoryItem(
            UUID id,
            String amount,
            String currency,
            String effectiveFrom,
            Instant changedAt,
            UUID changedBy
    ) {
        static HistoryItem from(IncomeHistoryEntry entry) {
            return new HistoryItem(
                    entry.id(), entry.amount().toPlainString(), entry.amount().currency(),
                    entry.effectiveFrom().toString(), entry.changedAt(), entry.changedBy());
        }
    }

    record HistoryResponse(List<HistoryItem> items, int page, int size, boolean hasNext) {
        static HistoryResponse from(IncomeHistoryPage history) {
            return new HistoryResponse(
                    history.items().stream().map(HistoryItem::from).toList(),
                    history.page(), history.size(), history.hasNext());
        }
    }
}
