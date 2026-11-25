/**
 * Eventos publicados pelo ledger após mutações de gastos (ADR-0033). Não carregam
 * valores monetários nem descrições.
 */
@org.springframework.modulith.NamedInterface("events")
package br.com.controlegastos.ledger.events;
