package br.com.controlegastos.ledger.web;

import br.com.controlegastos.ledger.application.LedgerService;
import br.com.controlegastos.ledger.domain.LedgerEntry;
import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/envelopes/{id}/entries")
public class LedgerEntryController {

    private final LedgerService ledger;

    LedgerEntryController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    EntryPageResponse list(@PathVariable UUID id,
                           @RequestParam(required = false) String month,
                           @RequestParam(required = false) String kind,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginação inválida");
        YearMonth ym = month == null ? null : parseMonth(month);
        LedgerKind lk = null;
        if (kind != null) {
            try { lk = LedgerKind.valueOf(kind); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Kind inválido"); }
        }
        Page<LedgerEntry> result = ledger.list(id, lk, ym, page, size);
        return EntryPageResponse.from(result);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EntryResponse create(@PathVariable UUID id, @RequestBody JsonNode body) {
        LedgerKind kind = kindField(body, "kind");
        Money amount = moneyField(body, "amount");
        LocalDate occurredAt = dateField(body, "occurredAt");
        String description = body.has("description") && !body.get("description").isNull()
                ? body.get("description").asString()
                : null;
        LedgerEntry entry = ledger.register(id, kind, amount, occurredAt, description);
        return EntryResponse.from(entry);
    }

    private YearMonth parseMonth(String month) {
        try { return YearMonth.parse(month); } catch (DateTimeParseException e) { throw new IllegalArgumentException("Mês inválido"); }
    }

    private LedgerKind kindField(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).isString()) throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        String raw = body.get(field).asString();
        try { return LedgerKind.valueOf(raw); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Kind inválido: " + raw); }
    }

    private Money moneyField(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).isObject()) throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        JsonNode node = body.get(field);
        if (!node.has("amount") || !node.get("amount").isString() || !node.has("currency")) throw new IllegalArgumentException("Money deve ter amount e currency");
        if (!"BRL".equals(node.get("currency").asString())) throw new IllegalArgumentException("Moeda deve ser BRL");
        String amountStr = node.get("amount").asString();
        if (!amountStr.matches("^\\d{1,17}\\.\\d{2}$")) throw new IllegalArgumentException("Amount deve ter exatamente duas casas decimais");
        if (node.size() != 2) throw new IllegalArgumentException("Money deve ter apenas amount e currency");
        return Money.brl(amountStr);
    }

    private LocalDate dateField(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).isString()) throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        String raw = body.get(field).asString();
        try { return LocalDate.parse(raw); } catch (Exception e) { throw new IllegalArgumentException("Data inválida, use YYYY-MM-DD"); }
    }

    record MoneyDTO(String amount, String currency) {}
    record EntryResponse(UUID id, UUID envelopeId, String kind, MoneyDTO amount, String occurredAt, String description, UUID authorId, Instant createdAt) {
        static EntryResponse from(LedgerEntry e) {
            return new EntryResponse(e.id(), e.envelopeId(), e.kind().name(),
                    new MoneyDTO(e.amount().toPlainString(), e.amount().currency()),
                    e.occurredAt().toString(), e.description(), e.authorId(), e.createdAt());
        }
    }
    record EntryPageResponse(List<EntryResponse> items, int page, int size, boolean hasNext) {
        static EntryPageResponse from(Page<LedgerEntry> page) {
            return new EntryPageResponse(page.getContent().stream().map(EntryResponse::from).toList(),
                    page.getNumber(), page.getSize(), page.hasNext());
        }
    }
}
