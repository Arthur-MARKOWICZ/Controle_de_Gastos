package br.com.controlegastos.ledger.web;

import br.com.controlegastos.ledger.application.LedgerService;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    private final LedgerService ledger;

    HistoryController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/history")
    HistoryPageResponse history(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return HistoryPageResponse.from(ledger.history(parseDate(from), parseDate(to), includeDeleted, page, size));
    }

    @GetMapping("/history/summary")
    HistorySummaryResponse summary(@RequestParam String from, @RequestParam String to) {
        return HistorySummaryResponse.from(ledger.historySummary(parseDate(from), parseDate(to)));
    }

    @PatchMapping("/ledger/entries/{id}")
    EntryResponse edit(@PathVariable UUID id, @RequestBody JsonNode body) {
        if (body.has("occurredAt")) throw new IllegalArgumentException("A data do gasto não pode ser alterada");
        if (!body.has("envelopeId") || !body.get("envelopeId").isString()) {
            throw new IllegalArgumentException("O campo envelopeId é obrigatório");
        }
        if (!body.has("amount") || !body.get("amount").isObject()) {
            throw new IllegalArgumentException("O campo amount é obrigatório");
        }
        if (body.size() > 3 || (body.has("description") && !body.get("description").isString() && !body.get("description").isNull())) {
            throw new IllegalArgumentException("Campos de edição inválidos");
        }
        UUID envelopeId;
        try {
            envelopeId = UUID.fromString(body.get("envelopeId").asString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("envelopeId inválido");
        }
        String description = body.has("description") && !body.get("description").isNull()
                ? body.get("description").asString() : null;
        return EntryResponse.from(ledger.editExpense(id, envelopeId, money(body.get("amount")), description));
    }

    @DeleteMapping("/ledger/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        ledger.deleteExpense(id);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Data inválida, use YYYY-MM-DD");
        }
    }

    private Money money(JsonNode node) {
        if (node.size() != 2 || !node.has("amount") || !node.get("amount").isString()
                || !node.has("currency") || !"BRL".equals(node.get("currency").asString())) {
            throw new IllegalArgumentException("Money inválido");
        }
        String amount = node.get("amount").asString();
        if (!amount.matches("^\\d{1,17}\\.\\d{2}$")) throw new IllegalArgumentException("Amount deve ter duas casas decimais");
        return Money.brl(amount);
    }

    record MoneyResponse(String amount, String currency) {
        static MoneyResponse from(Money amount) { return new MoneyResponse(amount.toPlainString(), amount.currency()); }
    }

    record EntryResponse(UUID id, UUID envelopeId, String kind, MoneyResponse amount, String occurredAt,
                         String description, UUID authorId, Instant createdAt, Instant deletedAt) {
        static EntryResponse from(LedgerEntry entry) {
            return new EntryResponse(entry.id(), entry.envelopeId(), entry.kind().name(), MoneyResponse.from(entry.amount()),
                    entry.occurredAt().toString(), entry.description(), entry.authorId(), entry.createdAt(), entry.deletedAt());
        }
    }

    record HistoryItemResponse(EntryResponse entry, String envelopeName, String purpose, String role) {
        static HistoryItemResponse from(LedgerService.HistoryEntry item) {
            return new HistoryItemResponse(EntryResponse.from(item.entry()), item.envelopeName(), item.purpose().name(), item.role());
        }
    }

    record HistoryPageResponse(List<HistoryItemResponse> items, int page, int size, boolean hasNext) {
        static HistoryPageResponse from(LedgerService.HistoryPage page) {
            return new HistoryPageResponse(page.items().stream().map(HistoryItemResponse::from).toList(), page.page(), page.size(), page.hasNext());
        }
    }

    record MonthlyTotalResponse(String month, MoneyResponse amount) {
        static MonthlyTotalResponse from(LedgerService.MonthlyTotal total) {
            return new MonthlyTotalResponse(total.month().toString(), MoneyResponse.from(total.amount()));
        }
    }

    record PurposeTotalResponse(String purpose, MoneyResponse amount) {
        static PurposeTotalResponse from(LedgerService.PurposeTotal total) {
            return new PurposeTotalResponse(total.purpose().name(), MoneyResponse.from(total.amount()));
        }
    }

    record HistorySummaryResponse(MoneyResponse income, MoneyResponse expenses, MoneyResponse netBalance,
                                  MoneyResponse accumulatedBalance, List<MonthlyTotalResponse> monthlyTotals,
                                  List<PurposeTotalResponse> purposeTotals) {
        static HistorySummaryResponse from(LedgerService.HistorySummary summary) {
            return new HistorySummaryResponse(MoneyResponse.from(summary.income()), MoneyResponse.from(summary.expenses()),
                    MoneyResponse.from(summary.netBalance()), MoneyResponse.from(summary.accumulatedBalance()),
                    summary.monthlyTotals().stream().map(MonthlyTotalResponse::from).toList(),
                    summary.purposeTotals().stream().map(PurposeTotalResponse::from).toList());
        }
    }
}
