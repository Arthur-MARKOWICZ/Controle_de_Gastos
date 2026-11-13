/**
 * Rotas de `/api/v1` que o primeiro corte do desktop consome.
 *
 * Saldo e lista de verbas saem de `GET /api/v1/ledger/summary` — não de
 * `GET /api/v1/envelopes`, onde `available` espelha `baseAmount` e
 * `isNegative` é sempre `false` por contrato documentado (ADR-0029).
 */

import { apiCall, IpcError } from "./ipc";
import type { Alert, LedgerSummary } from "./types";

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
    const parsed = JSON.parse(body) as { message?: unknown };
    if (typeof parsed.message === "string" && parsed.message.trim() !== "") {
      return parsed.message;
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
  method: "GET" | "POST" | "PATCH",
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

export { IpcError };
