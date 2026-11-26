package br.com.controlegastos.investments.web;

import br.com.controlegastos.investments.application.StockLotService;
import br.com.controlegastos.investments.domain.B3Ticker;
import br.com.controlegastos.investments.domain.StockLot;
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
@RequestMapping("/api/v1/investments/stocks")
class StockLotController {

    private final StockLotService service;

    StockLotController(StockLotService service) {
        this.service = service;
    }

    @GetMapping
    List<StockLotResponse> list() {
        return service.list().stream().map(StockLotResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StockLotResponse create(@RequestBody JsonNode body) {
        return StockLotResponse.from(service.register(command(body)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    private static StockLotService.Command command(JsonNode body) {
        if (!body.has("ticker") || !body.get("ticker").isString()) throw new IllegalArgumentException("O campo ticker é obrigatório");
        if (!body.has("quantity") || !body.get("quantity").isIntegralNumber() || !body.get("quantity").canConvertToLong()) {
            throw new IllegalArgumentException("O campo quantity deve ser um inteiro");
        }
        if (!body.has("purchaseDate") || !body.get("purchaseDate").isString()) {
            throw new IllegalArgumentException("O campo purchaseDate é obrigatório");
        }
        LocalDate purchaseDate;
        try {
            purchaseDate = LocalDate.parse(body.get("purchaseDate").asString());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("O campo purchaseDate deve ser uma data ISO", exception);
        }
        return new StockLotService.Command(B3Ticker.of(body.get("ticker").asString()), body.get("quantity").asLong(),
                MoneyJson.required(body, "unitPrice"), purchaseDate);
    }

    record StockLotResponse(UUID id, String ticker, long quantity, MoneyJson.Dto unitPrice,
                            MoneyJson.Dto acquisitionCost, String purchaseDate, String createdAt) {
        static StockLotResponse from(StockLot lot) {
            return new StockLotResponse(lot.id(), lot.ticker(), lot.quantity(), MoneyJson.Dto.of(lot.unitPrice()),
                    MoneyJson.Dto.of(lot.acquisitionCost()), lot.purchaseDate().toString(), lot.createdAt().toString());
        }
    }
}
