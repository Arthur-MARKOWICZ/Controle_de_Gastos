package br.com.controlegastos.investments.web;

import br.com.controlegastos.investments.application.CdbPositionService;
import br.com.controlegastos.investments.application.CdbPositionView;
import br.com.controlegastos.investments.domain.AnnualRate;
import br.com.controlegastos.investments.domain.CdbPosition;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/investments/cdbs")
class CdbPositionController {

    private final CdbPositionService service;

    CdbPositionController(CdbPositionService service) {
        this.service = service;
    }

    @GetMapping
    List<CdbPositionResponse> list() {
        return service.list().stream().map(CdbPositionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CdbPositionResponse create(@RequestBody JsonNode body) {
        return CdbPositionResponse.from(service.register(command(body)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    private static CdbPositionService.Command command(JsonNode body) {
        if (!body.has("annualRate") || !body.get("annualRate").isString()) {
            throw new IllegalArgumentException("O campo annualRate é obrigatório");
        }
        if (!body.has("startDate") || !body.get("startDate").isString()) {
            throw new IllegalArgumentException("O campo startDate é obrigatório");
        }
        if (!body.has("maturityDate") || !body.get("maturityDate").isString()) {
            throw new IllegalArgumentException("O campo maturityDate é obrigatório");
        }
        LocalDate startDate = parseDate(body.get("startDate").asString(), "startDate");
        LocalDate maturityDate = parseDate(body.get("maturityDate").asString(), "maturityDate");
        return new CdbPositionService.Command(
                MoneyJson.required(body, "principal"),
                AnnualRate.of(body.get("annualRate").asString()),
                startDate,
                maturityDate);
    }

    private static LocalDate parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("O campo " + field + " deve ser uma data ISO", exception);
        }
    }

    record CdbPositionResponse(
            UUID id,
            MoneyJson.Dto principal,
            String annualRate,
            String startDate,
            String maturityDate,
            MoneyJson.Dto estimatedValue,
            String createdAt) {

        static CdbPositionResponse from(CdbPositionView view) {
            CdbPosition position = view.position();
            return new CdbPositionResponse(
                    position.id(),
                    MoneyJson.Dto.of(position.principal()),
                    position.annualRate().toPlainString(),
                    position.startDate().toString(),
                    position.maturityDate().toString(),
                    MoneyJson.Dto.of(view.estimatedValue()),
                    position.createdAt().toString());
        }
    }
}
