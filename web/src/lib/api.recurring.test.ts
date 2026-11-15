import { describe, expect, it, vi } from "vitest";
import type { AuthClient } from "../auth/auth-client";
import { ApiError, createApiClient } from "./api";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function clientReturning(...responses: Response[]) {
  const request = vi.fn();
  for (const response of responses) request.mockResolvedValueOnce(response);
  return { api: createApiClient({ request } as unknown as AuthClient), request };
}

const money = (amount: string) => ({ amount, currency: "BRL" as const });

describe("recorrências no cliente da API", () => {
  it("cria recorrência com valor como string decimal", async () => {
    const created = { id: "r1", envelopeId: "e/1", kind: "EXPENSE", amount: money("1800.00"), dayOfMonth: 5,
      description: "Aluguel", active: true, firstCompetence: "2026-12", version: 0 };
    const { api, request } = clientReturning(jsonResponse(201, created));

    await expect(api.createRecurringEntry("e/1", { kind: "EXPENSE", amount: money("1800.00"), dayOfMonth: 5, description: "Aluguel" }))
      .resolves.toEqual(created);
    const [url, init] = request.mock.calls[0];
    expect(url).toBe("/api/v1/envelopes/e%2F1/recurring-entries");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ kind: "EXPENSE", amount: { amount: "1800.00", currency: "BRL" }, dayOfMonth: 5, description: "Aluguel" });
  });

  it("lista, atualiza e exclui recorrências", async () => {
    const { api, request } = clientReturning(jsonResponse(200, []), jsonResponse(200, {}), new Response(null, { status: 204 }));

    await api.listRecurringEntries("e1");
    await api.updateRecurringEntry("e1", "r1", { kind: "EXPENSE", amount: money("10.00"), dayOfMonth: 31, description: null, active: false });
    await expect(api.deleteRecurringEntry("e1", "r1")).resolves.toBeUndefined();

    expect(request.mock.calls[0][0]).toBe("/api/v1/envelopes/e1/recurring-entries");
    expect(request.mock.calls[1][0]).toBe("/api/v1/envelopes/e1/recurring-entries/r1");
    expect(request.mock.calls[1][1].method).toBe("PUT");
    expect(JSON.parse(request.mock.calls[1][1].body).active).toBe(false);
    expect(request.mock.calls[2][1].method).toBe("DELETE");
  });

  it("lista pendências, confirma com ajuste e descarta", async () => {
    const pending = { current: [], overdue: [] };
    const { api, request } = clientReturning(
      jsonResponse(200, pending),
      jsonResponse(200, { id: "o1", status: "CONFIRMED", ledgerEntryId: "l1" }),
      new Response(null, { status: 204 }),
    );

    await expect(api.listPendingOccurrences()).resolves.toEqual(pending);
    await api.confirmOccurrence("o1", { amount: money("237.45"), occurredAt: "2026-12-05" });
    await api.discardOccurrence("o2");

    expect(request.mock.calls[0][0]).toBe("/api/v1/recurring-occurrences/pending");
    expect(request.mock.calls[1][0]).toBe("/api/v1/recurring-occurrences/o1/confirm");
    expect(JSON.parse(request.mock.calls[1][1].body)).toEqual({ amount: { amount: "237.45", currency: "BRL" }, occurredAt: "2026-12-05" });
    expect(request.mock.calls[2][0]).toBe("/api/v1/recurring-occurrences/o2/discard");
  });

  it.each([
    [403, "FORBIDDEN"],
    [404, "RECURRING_NOT_FOUND"],
    [409, "RECURRING_OCCURRENCE_RESOLVED"],
  ])("propaga %s como ApiError com código", async (status, code) => {
    const { api } = clientReturning(jsonResponse(status, { status, code, detail: "d", title: "t" }));

    const error = await api.confirmOccurrence("o1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(status);
    expect((error as ApiError).code).toBe(code);
  });
});
