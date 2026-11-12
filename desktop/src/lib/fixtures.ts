/** Respostas de exemplo da API, com os valores dos cenários das specs. */

import type { Alert, LedgerSummary, SummaryEnvelope } from "./types";

export function envelope(overrides: Partial<SummaryEnvelope> = {}): SummaryEnvelope {
  return {
    id: "env-1",
    name: "Moradia",
    purpose: "FIXED",
    baseAmount: { amount: "1200.00", currency: "BRL" },
    available: { amount: "1200.00", currency: "BRL" },
    isNegative: false,
    role: "OWNER",
    wealthAllocation: false,
    archivedAt: null,
    ...overrides,
  };
}

export function summary(overrides: Partial<LedgerSummary> = {}): LedgerSummary {
  return {
    income: { amount: "8000.00", currency: "BRL", effectiveFrom: "2026-11" },
    allocated: { amount: "6450.00", currency: "BRL" },
    unallocated: { amount: "1550.00", currency: "BRL" },
    envelopes: [envelope()],
    ...overrides,
  };
}

export function alert(overrides: Partial<Alert> = {}): Alert {
  return {
    id: "alerta-1",
    type: "ENVELOPE_NEGATIVE_BALANCE",
    envelopeId: "env-combustivel",
    envelopeName: "Combustível",
    referenceMonth: "2026-11",
    ...overrides,
  };
}

export function summaryPath(month: string): string {
  return `/api/v1/ledger/summary?month=${month}`;
}

export const ALERTS_PATH = "/api/v1/alerts";
