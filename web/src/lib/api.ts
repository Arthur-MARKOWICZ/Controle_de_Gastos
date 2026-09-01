import { AuthClient } from "../auth/auth-client";

export type MoneyDTO = { amount: string; currency: "BRL" };
export type EnvelopePurpose = "LIMIT" | "GOAL" | "FIXED" | "SAVINGS_TARGET" | "ANNUAL_EXPENSE";
export type AnnualExpenseDTO = { annualAmount: MoneyDTO; dueMonth: number; dueDay: number; fundingMode: "MONTHLY" | "ONE_TIME" };

export type EnvelopeDTO = {
  id: string;
  ownerId: string;
  name: string;
  purpose: EnvelopePurpose;
  baseAmount: MoneyDTO;
  targetAmount: MoneyDTO | null;
  targetReachedAt: string | null;
  annualExpense?: AnnualExpenseDTO | null;
  available: MoneyDTO;
  isNegative: boolean;
  role: "OWNER" | "PARTICIPANT";
  createdAt: string;
  archivedAt: string | null;
  version: number;
};

export type CreateEnvelopeRequest = {
  name: string;
  purpose: EnvelopePurpose;
  baseAmount: MoneyDTO;
  targetAmount?: MoneyDTO;
  annualAmount?: MoneyDTO;
  dueMonth?: number;
  dueDay?: number;
  fundingMode?: "MONTHLY" | "ONE_TIME";
};

export type UpdateEnvelopeRequest = {
  name?: string;
  baseAmount?: MoneyDTO;
  purpose?: EnvelopePurpose;
  targetAmount?: MoneyDTO;
  annualAmount?: MoneyDTO;
  dueMonth?: number;
  dueDay?: number;
  fundingMode?: "MONTHLY" | "ONE_TIME";
};

export type LedgerEntryDTO = {
  id: string;
  envelopeId: string;
  kind: "EXPENSE" | "CONTRIBUTION";
  amount: MoneyDTO;
  occurredAt: string;
  description: string | null;
  authorId: string;
  createdAt: string;
  deletedAt?: string | null;
  targetJustReached?: boolean;
};

export type HistoryItemDTO = {
  entry: LedgerEntryDTO;
  envelopeName: string;
  purpose: EnvelopePurpose;
  role: "OWNER" | "PARTICIPANT";
};

export type HistoryPageDTO = { items: HistoryItemDTO[]; page: number; size: number; hasNext: boolean };
export type HistorySummaryDTO = {
  income: MoneyDTO;
  expenses: MoneyDTO;
  netBalance: MoneyDTO;
  accumulatedBalance: MoneyDTO;
  monthlyTotals: { month: string; amount: MoneyDTO }[];
  purposeTotals: { purpose: EnvelopePurpose; amount: MoneyDTO }[];
};

export type CreateEntryRequest = {
  kind: "EXPENSE" | "CONTRIBUTION";
  amount: MoneyDTO;
  occurredAt: string;
  description?: string | null;
};

export type LedgerSummaryDTO = {
  income: MoneyDTO & { effectiveFrom: string; changedAt: string } | null;
  allocated: MoneyDTO;
  unallocated: MoneyDTO;
  usagePct: number;
  envelopes: EnvelopeDTO[];
};

export type IncomeDTO = {
  amount: string;
  currency: "BRL";
  effectiveFrom: string;
  changedAt: string;
};

export type IncomeHistoryItemDTO = IncomeDTO & { id: string; changedBy: string };
export type IncomeHistoryPageDTO = { items: IncomeHistoryItemDTO[]; page: number; size: number; hasNext: boolean };
export type ReportId = "expenses-by-purpose" | "limit-exceeded-months" | "goals-below-target";
export type ReportFormat = "csv" | "xlsx";

export class ApiError extends Error {
  public readonly requiredMinimum?: string;
  public readonly shortfall?: string;
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly detail: string,
    public readonly title: string,
    extra?: { requiredMinimum?: string; shortfall?: string; currency?: string },
  ) {
    super(detail || title);
    this.requiredMinimum = extra?.requiredMinimum;
    this.shortfall = extra?.shortfall;
  }
}

async function parseProblem(response: Response): Promise<ApiError> {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    // ignore
  }
  if (body && typeof body === "object" && "code" in body) {
    const p = body as {
      code: string;
      detail: string;
      title: string;
      status: number;
      requiredMinimum?: string;
      shortfall?: string;
      currency?: string;
    };
    return new ApiError(response.status, p.code ?? "unknown", p.detail ?? "", p.title ?? "", {
      requiredMinimum: p.requiredMinimum,
      shortfall: p.shortfall,
      currency: p.currency,
    });
  }
  return new ApiError(response.status, "unknown", `Erro ${response.status}`, "Erro");
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.ok) {
    if (response.status === 204) return undefined as unknown as T;
    return (await response.json()) as T;
  }
  throw await parseProblem(response);
}

export function createApiClient(authClient: AuthClient) {
  return {
    getEnvelopes(month?: string): Promise<EnvelopeDTO[]> {
      const qs = month ? `?month=${encodeURIComponent(month)}` : "";
      return authClient.request(`/api/v1/envelopes${qs}`).then(handleResponse<EnvelopeDTO[]>);
    },
    getEnvelope(id: string): Promise<EnvelopeDTO> {
      return authClient.request(`/api/v1/envelopes/${encodeURIComponent(id)}`).then(handleResponse<EnvelopeDTO>);
    },
    createEnvelope(dto: CreateEnvelopeRequest): Promise<EnvelopeDTO> {
      return authClient
        .request("/api/v1/envelopes", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(dto),
        })
        .then(handleResponse<EnvelopeDTO>);
    },
    updateEnvelope(id: string, dto: UpdateEnvelopeRequest): Promise<EnvelopeDTO> {
      return authClient
        .request(`/api/v1/envelopes/${encodeURIComponent(id)}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(dto),
        })
        .then(handleResponse<EnvelopeDTO>);
    },
    archiveEnvelope(id: string): Promise<void> {
      return authClient
        .request(`/api/v1/envelopes/${encodeURIComponent(id)}/archive`, { method: "POST" })
        .then(handleResponse<void>);
    },
    getEntries(envelopeId: string, month?: string): Promise<{ items: LedgerEntryDTO[]; page: number; size: number; hasNext: boolean }> {
      const qs = month ? `?month=${encodeURIComponent(month)}` : "";
      return authClient
        .request(`/api/v1/envelopes/${encodeURIComponent(envelopeId)}/entries${qs}`)
        .then((r) => handleResponse<{ items: LedgerEntryDTO[]; page: number; size: number; hasNext: boolean }>(r));
    },
    createEntry(envelopeId: string, dto: CreateEntryRequest): Promise<LedgerEntryDTO> {
      return authClient
        .request(`/api/v1/envelopes/${encodeURIComponent(envelopeId)}/entries`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(dto),
        })
        .then(handleResponse<LedgerEntryDTO>);
    },
    getLedgerSummary(month?: string): Promise<LedgerSummaryDTO> {
      const qs = month ? `?month=${encodeURIComponent(month)}` : "";
      return authClient.request(`/api/v1/ledger/summary${qs}`).then(handleResponse<LedgerSummaryDTO>);
    },
    getIncome(month?: string): Promise<IncomeDTO> {
      const qs = month ? `?month=${encodeURIComponent(month)}` : "";
      return authClient.request(`/api/v1/income${qs}`).then(handleResponse<IncomeDTO>);
    },
    putIncome(amount: string): Promise<IncomeDTO> {
      return authClient
        .request("/api/v1/income", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ amount }),
        })
        .then(handleResponse<IncomeDTO>);
    },
    getIncomeHistory(page = 0, size = 20): Promise<IncomeHistoryPageDTO> {
      return authClient
        .request(`/api/v1/income/history?page=${page}&size=${size}`)
        .then(handleResponse<IncomeHistoryPageDTO>);
    },
    getHistory(from: string, to: string, page = 0, size = 10, includeDeleted = false): Promise<HistoryPageDTO> {
      const params = new URLSearchParams({ from, to, page: String(page), size: String(size) });
      if (includeDeleted) params.set("includeDeleted", "true");
      return authClient.request(`/api/v1/history?${params}`).then(handleResponse<HistoryPageDTO>);
    },
    getHistorySummary(from: string, to: string): Promise<HistorySummaryDTO> {
      const params = new URLSearchParams({ from, to });
      return authClient.request(`/api/v1/history/summary?${params}`).then(handleResponse<HistorySummaryDTO>);
    },
    async downloadReport(type: ReportId, from: string, to: string, format: ReportFormat): Promise<{ blob: Blob; filename: string }> {
      const params = new URLSearchParams({ from, to, format });
      const response = await authClient.request(`/api/v1/reports/${type}?${params}`);
      if (!response.ok) throw await parseProblem(response);
      const disposition = response.headers.get("Content-Disposition") ?? "";
      const encodedName = /filename\*=(?:UTF-8'')?([^;\s]+)/i.exec(disposition)?.[1]
        ?? /filename="?([^";]+)"?/i.exec(disposition)?.[1];
      const filename = encodedName ? decodeURIComponent(encodedName) : `${type}_${from}_${to}.${format}`;
      return { blob: await response.blob(), filename };
    },
    updateHistoryEntry(id: string, dto: { envelopeId: string; amount: MoneyDTO; description?: string | null }): Promise<LedgerEntryDTO> {
      return authClient.request(`/api/v1/ledger/entries/${encodeURIComponent(id)}`, {
        method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(dto),
      }).then(handleResponse<LedgerEntryDTO>);
    },
    deleteHistoryEntry(id: string): Promise<void> {
      return authClient.request(`/api/v1/ledger/entries/${encodeURIComponent(id)}`, { method: "DELETE" }).then(handleResponse<void>);
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
