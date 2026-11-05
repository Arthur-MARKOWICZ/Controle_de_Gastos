import { describe, expect, it, vi } from "vitest";
import {
  ApiError,
  AuthRequiredError,
  ControleGastosApiClient,
} from "./api-client.js";
import { MemorySessionStore } from "./session-store.js";

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function clientWithSession(
  fetchImpl: typeof fetch,
  accessToken = "secret-token",
) {
  const store = new MemorySessionStore({
    accessToken,
    refreshToken: "refresh-secret",
    refreshCookieName: "refresh_token",
    apiBaseUrl: "http://localhost:8080",
  });
  return {
    store,
    client: new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: store,
      fetchImpl,
    }),
  };
}

describe("ControleGastosApiClient", () => {
  it("getUserEmail returns only email from /users/me", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse({
        id: "11111111-1111-1111-1111-111111111111",
        email: "user@example.com",
        emailVerified: true,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-02T00:00:00Z",
      }),
    );
    const { client } = clientWithSession(fetchImpl);

    const result = await client.getUserEmail();

    expect(result).toEqual({ email: "user@example.com" });
    expect(Object.keys(result)).toEqual(["email"]);
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/users/me");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer secret-token");
  });

  it("registerExpense always posts kind EXPENSE", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ id: "e1" }, 201));
    const { client } = clientWithSession(fetchImpl, "tok");

    await client.registerExpense("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", {
      amount: { amount: "12.50", currency: "BRL" },
      occurredAt: "2026-11-05",
      description: "café",
    });

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(
      "http://localhost:8080/api/v1/envelopes/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/entries",
    );
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({
      kind: "EXPENSE",
      amount: { amount: "12.50", currency: "BRL" },
      occurredAt: "2026-11-05",
      description: "café",
    });
  });

  it("listEnvelopeEntries forces kind=EXPENSE", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ items: [] }));
    const { client } = clientWithSession(fetchImpl, "tok");

    await client.listEnvelopeEntries("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", {
      month: "2026-11",
      page: 1,
      size: 10,
    });

    const [url] = fetchImpl.mock.calls[0] as [string];
    expect(url).toContain("kind=EXPENSE");
    expect(url).toContain("month=2026-11");
  });

  it("createEnvelope and updateEnvelope hit the envelopes API", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ id: "c1" }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: "c1", version: 2 }));
    const { client } = clientWithSession(fetchImpl, "tok");

    await client.createEnvelope({
      name: "Mercado",
      purpose: "LIMIT",
      baseAmount: { amount: "400.00", currency: "BRL" },
    });
    await client.updateEnvelope("cccccccc-cccc-cccc-cccc-cccccccccccc", {
      version: 1,
      name: "Mercado mensal",
    });

    expect((fetchImpl.mock.calls[0]?.[1] as RequestInit).method).toBe("POST");
    expect((fetchImpl.mock.calls[1]?.[1] as RequestInit).method).toBe("PATCH");
  });

  it("surfaces API errors without attaching the token", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response('{"code":"bad.request"}', { status: 400 }),
    );
    const { client } = clientWithSession(fetchImpl, "super-secret");

    try {
      await client.listEnvelopes();
      expect.unreachable("expected ApiError");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 400,
        body: '{"code":"bad.request"}',
      });
      expect(String(error)).not.toContain("super-secret");
    }
  });

  it("listExpenses and getLedgerSummary build query strings", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ items: [] }))
      .mockResolvedValueOnce(jsonResponse({ envelopes: [] }));
    const { client } = clientWithSession(fetchImpl, "tok");

    await client.listExpenses({ from: "2026-11-01", to: "2026-11-30", page: 0, size: 20 });
    await client.getLedgerSummary("2026-11");

    expect(fetchImpl.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/api/v1/history?from=2026-11-01&to=2026-11-30&page=0&size=20",
    );
    expect(fetchImpl.mock.calls[1]?.[0]).toBe(
      "http://localhost:8080/api/v1/ledger/summary?month=2026-11",
    );
  });

  it("login persists session from Set-Cookie and does not return tokens", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse(
        { accessToken: "new-access", tokenType: "Bearer", expiresIn: 900 },
        200,
        {
          "set-cookie":
            "refresh_token=new-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Strict",
        },
      ),
    );
    const store = new MemorySessionStore();
    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: store,
      fetchImpl,
    });

    const result = await client.login("user@example.com", "password-long");

    expect(result).toEqual({ authenticated: true, expiresIn: 900 });
    expect(JSON.stringify(result)).not.toContain("new-access");
    expect(JSON.stringify(result)).not.toContain("new-refresh");
    expect(store.load()).toEqual({
      accessToken: "new-access",
      refreshToken: "new-refresh",
      refreshCookieName: "refresh_token",
      apiBaseUrl: "http://localhost:8080",
    });
  });

  it("login returns MFA challenge without saving a session", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse({ mfaRequired: true, challengeId: "chal-1", expiresIn: 300 }),
    );
    const store = new MemorySessionStore();
    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: store,
      fetchImpl,
    });

    await expect(client.login("user@example.com", "password-long")).resolves.toEqual({
      mfaRequired: true,
      challengeId: "chal-1",
      expiresIn: 300,
    });
    expect(store.load()).toBeNull();
  });

  it("refreshes once on 401 and retries the original call", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(
        jsonResponse(
          { accessToken: "refreshed-access", tokenType: "Bearer", expiresIn: 900 },
          200,
          {
            "set-cookie":
              "refresh_token=rotated-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Strict",
          },
        ),
      )
      .mockResolvedValueOnce(jsonResponse({ email: "user@example.com" }));

    const { client, store } = clientWithSession(fetchImpl, "expired-access");
    const result = await client.getUserEmail();

    expect(result).toEqual({ email: "user@example.com" });
    expect(fetchImpl).toHaveBeenCalledTimes(3);
    expect(fetchImpl.mock.calls[1]?.[0]).toBe("http://localhost:8080/api/v1/auth/refresh");
    expect(new Headers((fetchImpl.mock.calls[1]?.[1] as RequestInit).headers).get("Cookie")).toBe(
      "refresh_token=refresh-secret",
    );
    expect(store.load()?.accessToken).toBe("refreshed-access");
    expect(store.load()?.refreshToken).toBe("rotated-refresh");
    expect(new Headers((fetchImpl.mock.calls[2]?.[1] as RequestInit).headers).get("Authorization")).toBe(
      "Bearer refreshed-access",
    );
  });

  it("asks for login again when refresh fails", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 401 }));
    const { client, store } = clientWithSession(fetchImpl);

    await expect(client.listEnvelopes()).rejects.toBeInstanceOf(AuthRequiredError);
    expect(store.load()).toBeNull();
  });

  it("requires login when there is no session", async () => {
    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: new MemorySessionStore(),
      fetchImpl: vi.fn(),
    });
    await expect(client.listEnvelopes()).rejects.toBeInstanceOf(AuthRequiredError);
  });
});
