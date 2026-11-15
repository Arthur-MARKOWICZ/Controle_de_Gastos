import { describe, expect, it, vi } from "vitest";
import type { AuthClient } from "../auth/auth-client";
import { ApiError, createApiClient } from "./api";

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function problem(status: number, code: string, detail: string, extra: Record<string, unknown> = {}) {
  return jsonResponse(status, { status, code, detail, title: "Título", ...extra });
}

function clientReturning(...responses: Array<Response | Error>) {
  const request = vi.fn();
  for (const response of responses) {
    if (response instanceof Error) request.mockRejectedValueOnce(response);
    else request.mockResolvedValueOnce(response);
  }
  const api = createApiClient({ request } as unknown as AuthClient);
  return { api, request };
}

async function captureError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("a promessa deveria ter sido rejeitada");
}

describe("createApiClient", () => {
  it("devolve o corpo JSON das respostas de sucesso", async () => {
    const { api, request } = clientReturning(jsonResponse(200, { items: [] }));

    await expect(api.listAlerts()).resolves.toEqual({ items: [] });
    expect(request).toHaveBeenCalledWith("/api/v1/alerts");
  });

  it("resolve undefined para 204 sem tentar ler corpo", async () => {
    const { api } = clientReturning(new Response(null, { status: 204 }));

    await expect(api.dismissAlert("abc")).resolves.toBeUndefined();
  });

  it("codifica identificadores e mês na URL para evitar injeção de caminho", async () => {
    const { api, request } = clientReturning(jsonResponse(200, []));

    await api.getEnvelopes("2026-09&x=1");

    expect(request).toHaveBeenCalledWith("/api/v1/envelopes?month=2026-09%26x%3D1");
  });

  it("envia valores monetários como string decimal no corpo", async () => {
    const { api, request } = clientReturning(jsonResponse(200, { amount: "1000.00" }));

    await api.putIncome("1000.00");

    expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({ amount: "1000.00" });
  });

  it("transforma 401 em ApiError com status e código do problem", async () => {
    const { api } = clientReturning(problem(401, "AUTHENTICATION_FAILED", "Sessão expirada"));

    const error = await captureError(api.getLedgerSummary());

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.code).toBe("AUTHENTICATION_FAILED");
    expect(error.message).toBe("Sessão expirada");
  });

  it("transforma 403 de participante em ApiError com o detalhe da API", async () => {
    const { api } = clientReturning(problem(403, "FORBIDDEN", "Somente o proprietário pode editar a verba"));

    const error = await captureError(api.updateEnvelope("e1", { version: 0, name: "X" }));

    expect(error.status).toBe(403);
    expect(error.detail).toBe("Somente o proprietário pode editar a verba");
  });

  it("transforma 409 de concorrência em ApiError com o código ENVELOPE_CONCURRENT_CHANGE", async () => {
    const { api } = clientReturning(problem(409, "ENVELOPE_CONCURRENT_CHANGE", "A verba foi alterada"));

    const error = await captureError(api.updateEnvelope("e1", { version: 1 }));

    expect(error.status).toBe(409);
    expect(error.code).toBe("ENVELOPE_CONCURRENT_CHANGE");
  });

  it("preserva os campos monetários extras de um 422 de renda insuficiente", async () => {
    const { api } = clientReturning(
      problem(422, "INCOME_BELOW_ALLOCATIONS", "Renda abaixo das verbas", { requiredMinimum: "900.00", shortfall: "100.00" }),
    );

    const error = await captureError(api.putIncome("800.00"));

    expect(error.status).toBe(422);
    expect(error.requiredMinimum).toBe("900.00");
    expect(error.shortfall).toBe("100.00");
  });

  it("5xx sem corpo vira ApiError genérico com o status", async () => {
    const { api } = clientReturning(new Response("Bad Gateway", { status: 502 }));

    const error = await captureError(api.listAlerts());

    expect(error.status).toBe(502);
    expect(error.code).toBe("unknown");
    expect(error.message).toBe("Erro 502");
  });

  it("falha de rede propaga o erro original sem mascarar como ApiError", async () => {
    const { api } = clientReturning(new TypeError("Failed to fetch"));

    await expect(api.listAlerts()).rejects.toThrow(TypeError);
  });

  it("relatório usa o nome do arquivo do Content-Disposition codificado", async () => {
    const { api } = clientReturning(
      new Response("csv", {
        status: 200,
        headers: { "Content-Disposition": "attachment; filename*=UTF-8''gastos%20set.csv" },
      }),
    );

    const { filename } = await api.downloadReport("expenses-by-purpose", "2026-09-01", "2026-09-30", "csv");

    expect(filename).toBe("gastos set.csv");
  });

  it("relatório sem Content-Disposition usa nome padrão com tipo e período", async () => {
    const { api } = clientReturning(new Response("csv", { status: 200 }));

    const { filename } = await api.downloadReport("goals-below-target", "2026-09-01", "2026-09-30", "xlsx");

    expect(filename).toBe("goals-below-target_2026-09-01_2026-09-30.xlsx");
  });

  it("relatório com erro rejeita com ApiError do problem", async () => {
    const { api } = clientReturning(problem(400, "INVALID_REQUEST", "Este relatório exige meses completos"));

    const error = await captureError(api.downloadReport("limit-exceeded-months", "2026-09-02", "2026-09-30", "csv"));

    expect(error.status).toBe(400);
    expect(error.message).toBe("Este relatório exige meses completos");
  });
});
