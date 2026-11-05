export type Money = {
  amount: string;
  currency: "BRL";
};

export type EnvelopePurpose =
  | "LIMIT"
  | "GOAL"
  | "FIXED"
  | "SAVINGS_TARGET"
  | "ANNUAL_EXPENSE";

export type FundingMode = "MONTHLY" | "ONE_TIME";

export type CreateEnvelopeInput = {
  name: string;
  purpose: EnvelopePurpose;
  baseAmount: Money;
  targetAmount?: Money;
  annualAmount?: Money;
  dueMonth?: number;
  dueDay?: number;
  fundingMode?: FundingMode;
};

export type UpdateEnvelopeInput = {
  version: number;
  name?: string;
  purpose?: EnvelopePurpose;
  baseAmount?: Money;
  targetAmount?: Money;
  annualAmount?: Money;
  dueMonth?: number;
  dueDay?: number;
  fundingMode?: FundingMode;
};

export type RegisterExpenseInput = {
  amount: Money;
  occurredAt: string;
  description?: string | null;
};

export type ApiClientOptions = {
  apiBaseUrl: string;
  accessToken: string;
  fetchImpl?: typeof fetch;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
  ) {
    super(`API request failed with status ${status}`);
    this.name = "ApiError";
  }
}

/**
 * Cliente HTTP mínimo da API /api/v1. Não registra tokens nem corpos.
 */
export class ControleGastosApiClient {
  private readonly apiBaseUrl: string;
  private readonly accessToken: string;
  private readonly fetchImpl: typeof fetch;

  constructor(options: ApiClientOptions) {
    this.apiBaseUrl = options.apiBaseUrl.replace(/\/+$/, "");
    this.accessToken = options.accessToken;
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  /** Retorna somente o e-mail; demais campos de /users/me são descartados. */
  async getUserEmail(): Promise<{ email: string }> {
    const user = await this.requestJson<{ email?: unknown }>("/api/v1/users/me");
    if (typeof user.email !== "string" || user.email.length === 0) {
      throw new Error("API response did not include a usable email");
    }
    return { email: user.email };
  }

  listEnvelopes(month?: string): Promise<unknown> {
    return this.requestJson(this.withQuery("/api/v1/envelopes", { month }));
  }

  getEnvelope(id: string): Promise<unknown> {
    return this.requestJson(`/api/v1/envelopes/${encodeURIComponent(id)}`);
  }

  createEnvelope(body: CreateEnvelopeInput): Promise<unknown> {
    return this.requestJson("/api/v1/envelopes", {
      method: "POST",
      body: JSON.stringify(body),
    });
  }

  updateEnvelope(id: string, body: UpdateEnvelopeInput): Promise<unknown> {
    return this.requestJson(`/api/v1/envelopes/${encodeURIComponent(id)}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    });
  }

  listEnvelopeEntries(
    envelopeId: string,
    query: { month?: string; page?: number; size?: number } = {},
  ): Promise<unknown> {
    return this.requestJson(
      this.withQuery(`/api/v1/envelopes/${encodeURIComponent(envelopeId)}/entries`, {
        month: query.month,
        kind: "EXPENSE",
        page: query.page?.toString(),
        size: query.size?.toString(),
      }),
    );
  }

  registerExpense(envelopeId: string, input: RegisterExpenseInput): Promise<unknown> {
    return this.requestJson(
      `/api/v1/envelopes/${encodeURIComponent(envelopeId)}/entries`,
      {
        method: "POST",
        body: JSON.stringify({
          kind: "EXPENSE",
          amount: input.amount,
          occurredAt: input.occurredAt,
          description: input.description ?? null,
        }),
      },
    );
  }

  listExpenses(query: {
    from: string;
    to: string;
    page?: number;
    size?: number;
  }): Promise<unknown> {
    return this.requestJson(
      this.withQuery("/api/v1/history", {
        from: query.from,
        to: query.to,
        page: query.page?.toString(),
        size: query.size?.toString(),
      }),
    );
  }

  getLedgerSummary(month?: string): Promise<unknown> {
    return this.requestJson(this.withQuery("/api/v1/ledger/summary", { month }));
  }

  private withQuery(
    path: string,
    params: Record<string, string | undefined>,
  ): string {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== "") {
        search.set(key, value);
      }
    }
    const qs = search.toString();
    return qs ? `${path}?${qs}` : path;
  }

  private async requestJson<T = unknown>(
    path: string,
    init: RequestInit = {},
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${this.accessToken}`);
    headers.set("Accept", "application/json");
    if (init.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }

    const response = await this.fetchImpl(`${this.apiBaseUrl}${path}`, {
      ...init,
      headers,
    });

    const text = await response.text();
    if (!response.ok) {
      throw new ApiError(response.status, text);
    }
    if (!text) {
      return null as T;
    }
    return JSON.parse(text) as T;
  }
}
