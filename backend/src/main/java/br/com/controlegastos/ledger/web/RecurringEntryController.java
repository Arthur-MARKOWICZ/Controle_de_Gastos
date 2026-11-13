package br.com.controlegastos.ledger.web;

import br.com.controlegastos.ledger.application.RecurringEntryService;
import br.com.controlegastos.ledger.domain.LedgerKind;
import br.com.controlegastos.ledger.domain.RecurringEntry;
import br.com.controlegastos.shared.money.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/envelopes/{id}/recurring-entries")
class RecurringEntryController {

    private final RecurringEntryService service;

    RecurringEntryController(RecurringEntryService service) {
        this.service = service;
    }

    @GetMapping
    List<RecurringEntryResponse> list(@PathVariable UUID id) {
        return service.list(id).stream().map(RecurringEntryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RecurringEntryResponse create(@PathVariable UUID id, @RequestBody JsonNode body) {
        return RecurringEntryResponse.from(service.create(id, command(body)));
    }

    @PutMapping("/{recurringId}")
    RecurringEntryResponse update(@PathVariable UUID id, @PathVariable UUID recurringId, @RequestBody JsonNode body) {
        if (!body.has("active") || !body.get("active").isBoolean()) {
            throw new IllegalArgumentException("O campo active é obrigatório");
        }
        return RecurringEntryResponse.from(service.update(id, recurringId, command(body), body.get("active").asBoolean()));
    }

    @DeleteMapping("/{recurringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @PathVariable UUID recurringId) {
        service.delete(id, recurringId);
    }

    private RecurringEntryService.Command command(JsonNode body) {
        if (!body.has("kind") || !body.get("kind").isString()) throw new IllegalArgumentException("O campo kind é obrigatório");
        LedgerKind kind;
        try { kind = LedgerKind.valueOf(body.get("kind").asString()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Kind inválido"); }
        if (!body.has("dayOfMonth") || !body.get("dayOfMonth").isIntegralNumber()) {
            throw new IllegalArgumentException("O campo dayOfMonth é obrigatório");
        }
        String description = body.has("description") && !body.get("description").isNull()
                ? body.get("description").asString() : null;
        return new RecurringEntryService.Command(kind, MoneyJson.required(body, "amount"),
                body.get("dayOfMonth").asInt(), description);
    }

    record RecurringEntryResponse(UUID id, UUID envelopeId, String kind, MoneyJson.Dto amount, int dayOfMonth,
                                  String description, boolean active, String firstCompetence, long version) {
        static RecurringEntryResponse from(RecurringEntry e) {
            return new RecurringEntryResponse(e.id(), e.envelopeId(), e.kind().name(), MoneyJson.Dto.of(e.amount()),
                    e.dayOfMonth(), e.description(), e.active(), e.firstCompetence().toString(), e.version());
        }
    }
}
