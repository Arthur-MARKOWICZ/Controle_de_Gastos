/**
 * DTOs de `/api/v1` consumidos pelo desktop.
 *
 * Dinheiro é sempre string decimal com duas casas, como a API devolve. O
 * cliente não converte para número para calcular nada que o usuário veja como
 * saldo — a API é a fonte (ADR-006, ADR-0029).
 */

export type MoneyDTO = { amount: string; currency: string };

export type EnvelopePurpose =
  | "LIMIT"
  | "GOAL"
  | "FIXED"
  | "SAVINGS_TARGET"
  | "ANNUAL_EXPENSE";

export type EnvelopeRole = "OWNER" | "PARTICIPANT";

/**
 * Verba como vem no campo `envelopes` de `GET /api/v1/ledger/summary`.
 *
 * É daqui que sai o saldo, não de `GET /api/v1/envelopes`: naquele contrato
 * `available` espelha `baseAmount` e `isNegative` é sempre `false`.
 */
export type SummaryEnvelope = {
  id: string;
  name: string;
  purpose: EnvelopePurpose;
  baseAmount: MoneyDTO;
  available: MoneyDTO;
  isNegative: boolean;
  role: EnvelopeRole;
  wealthAllocation: boolean;
  archivedAt: string | null;
};

export type IncomeDTO = {
  amount: string;
  currency: string;
  effectiveFrom: string;
};

export type LedgerSummary = {
  /** `null` quando o titular não configurou renda no mês. */
  income: IncomeDTO | null;
  allocated: MoneyDTO;
  unallocated: MoneyDTO;
  envelopes: SummaryEnvelope[];
};

export type Alert = {
  id: string;
  type: string;
  envelopeId: string;
  envelopeName: string;
  referenceMonth: string;
};

export const PURPOSE_LABELS: Record<EnvelopePurpose, string> = {
  LIMIT: "Limite de gasto",
  GOAL: "Meta de aporte",
  FIXED: "Compromisso fixo",
  SAVINGS_TARGET: "Reserva",
  ANNUAL_EXPENSE: "Gasto anual",
};

/** Natureza em que o usuário registra gasto neste corte. */
export function isSpendable(envelope: SummaryEnvelope): boolean {
  return envelope.archivedAt === null;
}
