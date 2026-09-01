package br.com.controlegastos.envelopes.web;

import br.com.controlegastos.envelopes.application.EnvelopeService;
import br.com.controlegastos.envelopes.domain.Envelope;
import br.com.controlegastos.envelopes.domain.EnvelopePurpose;
import br.com.controlegastos.envelopes.domain.AnnualExpenseFundingMode;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.shared.money.Money;
import java.time.Instant;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/envelopes")
public class EnvelopeController {

    private final EnvelopeService envelopes;
    private final AuthenticationService authentication;

    EnvelopeController(EnvelopeService envelopes, AuthenticationService authentication) {
        this.envelopes = envelopes;
        this.authentication = authentication;
    }

    @GetMapping
    List<EnvelopeResponse> list(@RequestParam(required = false) String month) {
        YearMonth ym = parseMonthOrCurrent(month);
        var visibles = envelopes.listVisible();
        return visibles.stream().map(e -> toResponse(e, ym)).toList();
    }

    @GetMapping("/{id}")
    EnvelopeResponse get(@PathVariable UUID id, @RequestParam(required = false) String month) {
        YearMonth ym = parseMonthOrCurrent(month);
        Envelope envelope = envelopes.getVisible(id);
        return toResponse(envelope, ym);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EnvelopeResponse create(@RequestBody JsonNode body) {
        String name = textField(body, "name");
        EnvelopePurpose purpose = purposeField(body, "purpose");
        Money baseAmount = purpose == EnvelopePurpose.ANNUAL_EXPENSE ? Money.zero() : moneyField(body, "baseAmount");
        Money targetAmount = body.has("targetAmount") ? moneyField(body, "targetAmount") : null;
        Envelope created = purpose == EnvelopePurpose.ANNUAL_EXPENSE
                ? envelopes.createAnnualExpense(name, moneyField(body, "annualAmount"), dueDateField(body), fundingModeField(body))
                : envelopes.create(name, purpose, baseAmount, targetAmount);
        YearMonth current = YearMonth.now(EnvelopeService.BUSINESS_ZONE);
        return toResponse(created, current);
    }

    @PatchMapping("/{id}")
    EnvelopeResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        if (body == null || !body.isObject() || body.size() == 0) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualização");
        }
        String name = body.has("name") ? textField(body, "name") : null;
        EnvelopePurpose purpose = body.has("purpose") ? purposeField(body, "purpose") : null;
        Money baseAmount = body.has("baseAmount") ? moneyField(body, "baseAmount") : null;
        Money targetAmount = body.has("targetAmount") ? moneyField(body, "targetAmount") : null;
        Money annualAmount = body.has("annualAmount") ? moneyField(body, "annualAmount") : null;
        MonthDay dueDate = body.has("dueMonth") || body.has("dueDay") ? dueDateField(body) : null;
        AnnualExpenseFundingMode fundingMode = body.has("fundingMode") ? fundingModeField(body) : null;
        // Validate no extra fields
        var fieldIt = body.properties().iterator();
        while (fieldIt.hasNext()) {
            String f = fieldIt.next().getKey();
            if (!List.of("name", "purpose", "baseAmount", "targetAmount", "annualAmount", "dueMonth", "dueDay", "fundingMode").contains(f)) {
                throw new IllegalArgumentException("Campo não permitido: " + f);
            }
        }
        Envelope updated = annualAmount != null || dueDate != null || fundingMode != null
                ? envelopes.updateAnnualExpense(id, name, annualAmount, dueDate, fundingMode)
                : envelopes.update(id, name, purpose, baseAmount, targetAmount);
        YearMonth current = YearMonth.now(EnvelopeService.BUSINESS_ZONE);
        return toResponse(updated, current);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID id) {
        envelopes.archive(id);
    }

    private YearMonth parseMonthOrCurrent(String month) {
        if (month == null) return YearMonth.now(EnvelopeService.BUSINESS_ZONE);
        return parseMonth(month);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("O mês deve usar o formato YYYY-MM");
        }
    }

    private String textField(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).isString()) {
            throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        }
        String value = body.get(field).asString();
        if (field.equals("name")) {
            if (value == null || value.trim().isEmpty() || value.trim().length() > 80) {
                throw new IllegalArgumentException("O nome deve ter entre 1 e 80 caracteres");
            }
            return value.trim();
        }
        return value;
    }

    private EnvelopePurpose purposeField(JsonNode body, String field) {
        String raw = textField(body, field);
        try {
            return EnvelopePurpose.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Propósito inválido: " + raw);
        }
    }

    private Money moneyField(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).isObject()) {
            throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        }
        JsonNode node = body.get(field);
        if (!node.has("amount") || !node.get("amount").isString() || !node.has("currency")) {
            throw new IllegalArgumentException("Money deve ter amount e currency");
        }
        if (!"BRL".equals(node.get("currency").asString())) {
            throw new IllegalArgumentException("Moeda deve ser BRL");
        }
        String amountStr = node.get("amount").asString();
        if (!amountStr.matches("^\\d{1,17}\\.\\d{2}$")) {
            throw new IllegalArgumentException("Amount deve ter exatamente duas casas decimais");
        }
        if (node.size() != 2) throw new IllegalArgumentException("Money deve ter apenas amount e currency");
        return Money.brl(amountStr);
    }

    private MonthDay dueDateField(JsonNode body) {
        if (!body.has("dueMonth") || !body.has("dueDay") || !body.get("dueMonth").isInt() || !body.get("dueDay").isInt()) {
            throw new IllegalArgumentException("Informe mês e dia de vencimento");
        }
        try { return MonthDay.of(body.get("dueMonth").asInt(), body.get("dueDay").asInt()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Data de vencimento inválida"); }
    }

    private AnnualExpenseFundingMode fundingModeField(JsonNode body) {
        if (!body.has("fundingMode") || !body.get("fundingMode").isString()) throw new IllegalArgumentException("Informe o modo de provisão");
        try { return AnnualExpenseFundingMode.valueOf(body.get("fundingMode").asString()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Modo de provisão inválido"); }
    }

    private EnvelopeResponse toResponse(Envelope e, YearMonth month) {
        // Sem dependência em ledger para quebrar ciclo Modulith; disponível inicial = baseAmount.
        // Saldo real com carry e ledger é fornecido por GET /ledger/summary (ledger module).
        Money available = e.baseAmount();
        boolean isNegative = false;
        UUID currentUser = authentication.currentUserId();
        String role = e.ownerId().equals(currentUser) ? "OWNER" : "PARTICIPANT";
        return new EnvelopeResponse(
                e.id(), e.ownerId(), e.name(), e.purpose().name(),
                new MoneyDTO(e.baseAmount().toPlainString(), e.baseAmount().currency()),
                e.targetAmount() == null ? null : new MoneyDTO(e.targetAmount().toPlainString(), e.targetAmount().currency()),
                e.targetReachedAt(),
                e.annualAmount() == null ? null : new AnnualExpenseDTO(new MoneyDTO(e.annualAmount().toPlainString(), e.annualAmount().currency()),
                        e.annualDueDate().getMonthValue(), e.annualDueDate().getDayOfMonth(), e.annualFundingMode().name()),
                new MoneyDTO(available.toPlainString(), available.currency()),
                isNegative, role,
                e.createdAt(), e.archivedAt(), e.version()
        );
    }

    record MoneyDTO(String amount, String currency) {}
    record AnnualExpenseDTO(MoneyDTO annualAmount, int dueMonth, int dueDay, String fundingMode) {}

    record EnvelopeResponse(UUID id, UUID ownerId, String name, String purpose,
                            MoneyDTO baseAmount, MoneyDTO targetAmount, Instant targetReachedAt, AnnualExpenseDTO annualExpense, MoneyDTO available,
                            boolean isNegative, String role,
                            Instant createdAt, Instant archivedAt, long version) {}
}
