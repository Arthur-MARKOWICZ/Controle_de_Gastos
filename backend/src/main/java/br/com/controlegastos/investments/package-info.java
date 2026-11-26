/**
 * Carteira de investimentos do titular (ADR-0034). Isolado do orçamento: depende
 * só de dinheiro ({@code shared.money}) e do titular autenticado ({@code identity}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Investimentos")
package br.com.controlegastos.investments;
