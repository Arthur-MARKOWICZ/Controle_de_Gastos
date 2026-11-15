package br.com.controlegastos.ledger.web;

import br.com.controlegastos.ledger.application.RecurringOccurrenceService;
import br.com.controlegastos.ledger.domain.RecurringOccurrence;
import br.com.controlegastos.ledger.infrastructure.RecurringOccurrenceRepository.PendingOccurrenceView;
import br.com.controlegastos.shared.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/recurring-occurrences")
class RecurringOccurrenceController {

    private final RecurringOccurrenceService service;

    RecurringOccurrenceController(RecurringOccurrenceService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    PendingResponse pending() {
        RecurringOccurrenceService.Pending pending = service.pending();
        return new PendingResponse(pending.current().stream().map(PendingItem::from).toList(),
                pending.overdue().stream().map(PendingItem::from).toList());
    }

    @PostMapping("/{occurrenceId}/confirm")
    ConfirmationResponse confirm(@PathVariable UUID occurrenceId, @RequestBody(required = false) JsonNode body) {
        Money amount = MoneyJson.optional(body, "amount");
        LocalDate occurredAt = null;
        if (body != null && body.has("occurredAt") && !body.get("occurredAt").isNull()) {
            try { occurredAt = LocalDate.parse(body.get("occurredAt").asString()); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("Data inválida, use YYYY-MM-DD"); }
        }
        RecurringOccurrence occurrence = service.confirm(occurrenceId, amount, occurredAt);
        return new ConfirmationResponse(occurrence.id(), occurrence.status().name(), occurrence.ledgerEntryId());
    }

    @PostMapping("/{occurrenceId}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@PathVariable UUID occurrenceId) {
        service.discard(occurrenceId);
    }

    record PendingResponse(List<PendingItem> current, List<PendingItem> overdue) {}

    record PendingItem(UUID id, UUID recurringEntryId, UUID envelopeId, String envelopeName, String kind,
                       String description, MoneyJson.Dto suggestedAmount, String dueDate, String competence) {
        static PendingItem from(PendingOccurrenceView v) {
            return new PendingItem(v.getId(), v.getRecurringEntryId(), v.getEnvelopeId(), v.getEnvelopeName(),
                    v.getKind(), v.getDescription(), MoneyJson.Dto.of(Money.brl(v.getSuggestedAmount())),
                    v.getDueDate().toString(), YearMonth.from(v.getCompetence()).toString());
        }
    }

    record ConfirmationResponse(UUID id, String status, UUID ledgerEntryId) {}
}
