/**
 * Reservado a eventos transversais de auditoria. A trilha de edições/exclusões
 * de gastos vive em {@code ledger_entry_revision} (ADR-0027 / módulo ledger),
 * no mesmo padrão de {@code income_revision}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Auditoria")
package br.com.controlegastos.audit;
