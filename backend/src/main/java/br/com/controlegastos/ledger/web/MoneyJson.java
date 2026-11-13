package br.com.controlegastos.ledger.web;

import br.com.controlegastos.shared.money.Money;
import tools.jackson.databind.JsonNode;

/** Leitura e escrita de dinheiro como {amount: "0.00", currency: "BRL"} (ADR-0006). */
final class MoneyJson {

    private MoneyJson() {
    }

    record Dto(String amount, String currency) {
        static Dto of(Money money) {
            return new Dto(money.toPlainString(), money.currency());
        }
    }

    static Money required(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).isObject()) throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        return parse(body.get(field));
    }

    static Money optional(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) return null;
        if (!body.get(field).isObject()) throw new IllegalArgumentException("O campo " + field + " é inválido");
        return parse(body.get(field));
    }

    private static Money parse(JsonNode node) {
        if (!node.has("amount") || !node.get("amount").isString() || !node.has("currency")) {
            throw new IllegalArgumentException("Money deve ter amount e currency");
        }
        if (!"BRL".equals(node.get("currency").asString())) throw new IllegalArgumentException("Moeda deve ser BRL");
        String amount = node.get("amount").asString();
        if (!amount.matches("^\\d{1,17}\\.\\d{2}$")) throw new IllegalArgumentException("Amount deve ter exatamente duas casas decimais");
        if (node.size() != 2) throw new IllegalArgumentException("Money deve ter apenas amount e currency");
        return Money.brl(amount);
    }
}
