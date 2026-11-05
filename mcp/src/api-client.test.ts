import { describe, expect, it, vi } from "vitest";
import { ApiError, ControleGastosApiClient } from "./api-client.js";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
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

    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      accessToken: "secret-token",
      fetchImpl,
    });

    const result = await client.getUserEmail();

    expect(result).toEqual({ email: "user@example.com" });
    expect(Object.keys(result)).toEqual(["email"]);
    expect(fetchImpl).toHaveBeenCalledOnce();
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/users/me");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer secret-token");
  });

  it("registerExpense always posts kind EXPENSE", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ id: "e1" }, 201));
    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080/",
      accessToken: "tok",
      fetchImpl,
    });

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
    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      accessToken: "tok",
      fetchImpl,
    });

    await client.listEnvelopeEntries("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", {
      month: "2026-11",
      page: 1,
      size: 10,
    });

    const [url] = fetchImpl.mock.calls[0] as [string];
    expect(url).toContain("kind=EXPENSE");
    expect(url).toContain("month=2026-11");
    expect(url).toContain("page=1");
    expect(url).toContain("size=10");
  });

  it("createEnvelope and updateEnvelope hit the envelopes API", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ id: "c1" }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: "c1", version: 2 }));

    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      accessToken: "tok",
      fetchImpl,
    });

    await client.createEnvelope({
      name: "Mercado",
      purpose: "LIMIT",
      baseAmount: { amount: "400.00", currency: "BRL" },
    });
    await client.updateEnvelope("cccccccc-cccc-cccc-cccc-cccccccccccc", {
      version: 1,
      name: "Mercado mensal",
    });

    expect(fetchImpl.mock.calls[0]?.[0]).toBe("http://localhost:8080/api/v1/envelopes");
    expect((fetchImpl.mock.calls[0]?.[1] as RequestInit).method).toBe("POST");
    expect(fetchImpl.mock.calls[1]?.[0]).toBe(
      "http://localhost:8080/api/v1/envelopes/cccccccc-cccc-cccc-cccc-cccccccccccc",
    );
    expect((fetchImpl.mock.calls[1]?.[1] as RequestInit).method).toBe("PATCH");
  });

  it("surfaces API errors without attaching the token", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response('{"code":"auth.invalid"}', { status: 401 }),
    );
    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      accessToken: "super-secret",
      fetchImpl,
    });

    try {
      await client.listEnvelopes();
      expect.unreachable("expected ApiError");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 401,
        body: '{"code":"auth.invalid"}',
      });
      expect(String(error)).not.toContain("super-secret");
    }
  });

  it("listExpenses and getLedgerSummary build query strings", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ items: [] }))
      .mockResolvedValueOnce(jsonResponse({ envelopes: [] }));

    const client = new ControleGastosApiClient({
      apiBaseUrl: "http://localhost:8080",
      accessToken: "tok",
      fetchImpl,
    });

    await client.listExpenses({ from: "2026-11-01", to: "2026-11-30", page: 0, size: 20 });
    await client.getLedgerSummary("2026-11");

    expect(fetchImpl.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/api/v1/history?from=2026-11-01&to=2026-11-30&page=0&size=20",
    );
    expect(fetchImpl.mock.calls[1]?.[0]).toBe(
      "http://localhost:8080/api/v1/ledger/summary?month=2026-11",
    );
  });
});
