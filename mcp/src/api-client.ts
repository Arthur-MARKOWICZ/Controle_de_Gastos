import { parseRefreshCookie, readSetCookieHeaders } from "./cookies.js";
import type { SessionStore } from "./session-store.js";

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

export type LoginSuccess = {
  authenticated: true;
  expiresIn: number;
};

export type LoginMfaChallenge = {
  mfaRequired: true;
  challengeId: string;
  expiresIn: number;
};

export type LoginResult = LoginSuccess | LoginMfaChallenge;

export type ApiClientOptions = {
  apiBaseUrl: string;
  sessionStore: SessionStore;
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

export class AuthRequiredError extends Error {
  constructor(
    message = "Sessão ausente ou expirada. Chame a tool login de novo.",
  ) {
    super(message);
    this.name = "AuthRequiredError";
  }
}

/**
 * Cliente HTTP mínimo da API /api/v1.
 * Sessão estilo mobile: access + refresh local; refresh em 401.
 * Não registra tokens, cookies, senhas nem corpos.
 */
export class ControleGastosApiClient {
  private readonly apiBaseUrl: string;
  private readonly sessionStore: SessionStore;
  private readonly fetchImpl: typeof fetch;
  private refreshInFlight: Promise<void> | null = null;

  constructor(options: ApiClientOptions) {
    this.apiBaseUrl = options.apiBaseUrl.replace(/\/+$/, "");
    this.sessionStore = options.sessionStore;
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async login(email: string, password: string): Promise<LoginResult> {
    const response = await this.rawFetch("/api/v1/auth/login", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password }),
    });
    const text = await response.text();
    if (!response.ok) {
      throw new ApiError(response.status, text);
    }
    const body = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    if (body.mfaRequired === true) {
      if (typeof body.challengeId !== "string" || typeof body.expiresIn !== "number") {
        throw new Error("Resposta MFA incompleta da API");
      }
      return {
        mfaRequired: true,
        challengeId: body.challengeId,
        expiresIn: body.expiresIn,
      };
    }
    this.persistSessionFromAuthResponse(response, body);
    return {
      authenticated: true,
      expiresIn: typeof body.expiresIn === "number" ? body.expiresIn : 900,
    };
  }

  async verifyMfa(challengeId: string, code: string): Promise<LoginSuccess> {
    const response = await this.rawFetch("/api/v1/auth/mfa/verify", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ challengeId, code }),
    });
    const text = await response.text();
    if (!response.ok) {
      throw new ApiError(response.status, text);
    }
    const body = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    this.persistSessionFromAuthResponse(response, body);
    return {
      authenticated: true,
      expiresIn: typeof body.expiresIn === "number" ? body.expiresIn : 900,
    };
  }

  async logout(): Promise<void> {
    const session = this.sessionStore.load();
    try {
      if (session) {
        await this.rawFetch("/api/v1/auth/logout", {
          method: "POST",
          headers: {
            Authorization: `Bearer ${session.accessToken}`,
            Accept: "application/json",
          },
        });
      }
    } finally {
      this.sessionStore.clear();
    }
  }

  authStatus(): { authenticated: boolean } {
    return { authenticated: this.sessionStore.load() !== null };
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

  private persistSessionFromAuthResponse(
    response: Response,
    body: Record<string, unknown>,
  ): void {
    if (typeof body.accessToken !== "string" || body.accessToken.length === 0) {
      throw new Error("Resposta de autenticação sem accessToken");
    }
    const cookie = parseRefreshCookie(readSetCookieHeaders(response.headers));
    if (!cookie) {
      throw new Error("Resposta de autenticação sem cookie de refresh");
    }
    this.sessionStore.save({
      accessToken: body.accessToken,
      refreshToken: cookie.value,
      refreshCookieName: cookie.name,
      apiBaseUrl: this.apiBaseUrl,
    });
  }

  private async refreshSession(): Promise<void> {
    if (this.refreshInFlight) {
      await this.refreshInFlight;
      return;
    }
    this.refreshInFlight = this.doRefresh().finally(() => {
      this.refreshInFlight = null;
    });
    await this.refreshInFlight;
  }

  private async doRefresh(): Promise<void> {
    const session = this.sessionStore.load();
    if (!session) {
      throw new AuthRequiredError();
    }
    const response = await this.rawFetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: {
        Accept: "application/json",
        Cookie: `${session.refreshCookieName}=${session.refreshToken}`,
      },
    });
    const text = await response.text();
    if (!response.ok) {
      this.sessionStore.clear();
      throw new AuthRequiredError();
    }
    const body = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    try {
      this.persistSessionFromAuthResponse(response, body);
    } catch {
      this.sessionStore.clear();
      throw new AuthRequiredError();
    }
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
    allowRefresh = true,
  ): Promise<T> {
    const session = this.sessionStore.load();
    if (!session) {
      throw new AuthRequiredError();
    }

    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${session.accessToken}`);
    headers.set("Accept", "application/json");
    if (init.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }

    const response = await this.rawFetch(path, {
      ...init,
      headers,
    });

    if (response.status === 401 && allowRefresh) {
      await this.refreshSession();
      return this.requestJson(path, init, false);
    }

    const text = await response.text();
    if (!response.ok) {
      if (response.status === 401) {
        this.sessionStore.clear();
        throw new AuthRequiredError();
      }
      throw new ApiError(response.status, text);
    }
    if (!text) {
      return null as T;
    }
    return JSON.parse(text) as T;
  }

  private rawFetch(path: string, init: RequestInit): Promise<Response> {
    return this.fetchImpl(`${this.apiBaseUrl}${path}`, init);
  }
}
