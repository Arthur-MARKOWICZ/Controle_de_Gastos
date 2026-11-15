/**
 * Rotas de `/api/v1` que o primeiro corte do desktop consome.
 *
 * Saldo e lista de verbas saem de `GET /api/v1/ledger/summary` — não de
 * `GET /api/v1/envelopes`, onde `available` espelha `baseAmount` e
 * `isNegative` é sempre `false` por contrato documentado (ADR-0029).
 */

import { apiCall, IpcError, type ApiMethod } from "./ipc";
import type {
  Alert,
  LedgerSummary,
  PendingOccurrences,
  RecurringEntry,
  RecurringEntryInput,
} from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function messageFor(status: number, body: string): string {
  try {
    // `problem+json` da API traz o texto em `detail`; `message` fica por compatibilidade.
    const parsed = JSON.parse(body) as { message?: unknown; detail?: unknown };
    for (const text of [parsed.message, parsed.detail]) {
      if (typeof text === "string" && text.trim() !== "") return text;
    }
  } catch {
    // corpo sem JSON: cai na mensagem por status
  }
  if (status === 403) return "Você não tem acesso a esta verba.";
  if (status === 404) return "Recurso não encontrado.";
  if (status >= 500) return "A API falhou. Tente de novo em instantes.";
  return `A API recusou a operação (HTTP ${status}).`;
}

async function request<T>(
  method: ApiMethod,
  path: string,
  body?: unknown,
): Promise<T> {
  const reply = await apiCall(method, path, body);
  if (reply.status < 200 || reply.status >= 300) {
    throw new ApiError(reply.status, messageFor(reply.status, reply.body));
  }
  if (!reply.body || reply.body.trim() === "") {
    return null as T;
  }
  return JSON.parse(reply.body) as T;
}

export function getLedgerSummary(month: string): Promise<LedgerSummary> {
  return request<LedgerSummary>(
    "GET",
    `/api/v1/ledger/summary?month=${encodeURIComponent(month)}`,
  );
}

export function listAlerts(): Promise<{ items: Alert[] }> {
  return request<{ items: Alert[] }>("GET", "/api/v1/alerts");
}

export function dismissAlert(id: string): Promise<void> {
  return request<void>("POST", `/api/v1/alerts/${encodeURIComponent(id)}/dismiss`);
}

export type RegisterExpenseInput = {
  /** String decimal com duas casas, já validada. */
  amount: string;
  /** `YYYY-MM-DD` no calendário America/Sao_Paulo. */
  occurredAt: string;
  description: string | null;
};

export function registerExpense(
  envelopeId: string,
  input: RegisterExpenseInput,
): Promise<unknown> {
  return request(
    "POST",
    `/api/v1/envelopes/${encodeURIComponent(envelopeId)}/entries`,
    {
      kind: "EXPENSE",
      amount: { amount: input.amount, currency: "BRL" },
      occurredAt: input.occurredAt,
      description: input.description,
    },
  );
}

export const PENDING_OCCURRENCES_PATH = "/api/v1/recurring-occurrences/pending";

export function listPendingOccurrences(): Promise<PendingOccurrences> {
  return request<PendingOccurrences>("GET", PENDING_OCCURRENCES_PATH);
}

/** Sem ajuste, a API usa o valor sugerido e a data prevista. */
export function confirmOccurrence(
  id: string,
  adjustment?: { amount: string; occurredAt: string },
): Promise<unknown> {
  return request("POST", `/api/v1/recurring-occurrences/${encodeURIComponent(id)}/confirm`,
    adjustment
      ? { amount: { amount: adjustment.amount, currency: "BRL" }, occurredAt: adjustment.occurredAt }
      : {});
}

export function discardOccurrence(id: string): Promise<void> {
  return request<void>("POST", `/api/v1/recurring-occurrences/${encodeURIComponent(id)}/discard`);
}

export function recurringPath(envelopeId: string): string {
  return `/api/v1/envelopes/${encodeURIComponent(envelopeId)}/recurring-entries`;
}

export function listRecurringEntries(envelopeId: string): Promise<RecurringEntry[]> {
  return request<RecurringEntry[]>("GET", recurringPath(envelopeId));
}

export function createRecurringEntry(envelopeId: string, input: RecurringEntryInput): Promise<RecurringEntry> {
  return request<RecurringEntry>("POST", recurringPath(envelopeId), {
    kind: input.kind,
    amount: { amount: input.amount, currency: "BRL" },
    dayOfMonth: input.dayOfMonth,
    description: input.description,
  });
}

export function updateRecurringEntry(
  envelopeId: string,
  id: string,
  input: RecurringEntryInput & { active: boolean },
): Promise<RecurringEntry> {
  return request<RecurringEntry>("PUT", `${recurringPath(envelopeId)}/${encodeURIComponent(id)}`, {
    kind: input.kind,
    amount: { amount: input.amount, currency: "BRL" },
    dayOfMonth: input.dayOfMonth,
    description: input.description,
    active: input.active,
  });
}

/** Exclusão lógica: lançamentos já confirmados permanecem; pendências são descartadas. */
export function deleteRecurringEntry(envelopeId: string, id: string): Promise<void> {
  return request<void>("DELETE", `${recurringPath(envelopeId)}/${encodeURIComponent(id)}`);
}

export { IpcError };
