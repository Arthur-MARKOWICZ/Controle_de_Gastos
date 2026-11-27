import { describe, expect, it, vi } from "vitest";
import type { AuthClient } from "../auth/auth-client";
import { ApiError, createApiClient, type PortfolioSummaryDTO } from "./api";

function clientReturning(response: Response) {
  const request = vi.fn().mockResolvedValue(response);
  return { api: createApiClient({ request } as unknown as AuthClient), request };
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("API de investimentos (carteira)", () => {
  it("lê o resumo da carteira em /api/v1/investments/summary com valores monetários como string", async () => {
    const summary: PortfolioSummaryDTO = {
      complete: true,
      totalCost: { amount: "11000.00", currency: "BRL" },
      totalCurrent: { amount: "12450.00", currency: "BRL" },
      totalPnl: { amount: "1450.00", currency: "BRL" },
      stocks: [
        {
          ticker: "PETR4",
          quantity: 100,
          averagePrice: { amount: "10.00", currency: "BRL" },
          acquisitionCost: { amount: "1000.00", currency: "BRL" },
          currentValue: { amount: "1200.00", currency: "BRL" },
          pnl: { amount: "200.00", currency: "BRL" },
          lots: [
            {
              id: "lot-1",
              quantity: 100,
              unitPrice: { amount: "10.00", currency: "BRL" },
              acquisitionCost: { amount: "1000.00", currency: "BRL" },
              purchaseDate: "2026-01-15",
              currentValue: { amount: "1200.00", currency: "BRL" },
              pnl: { amount: "200.00", currency: "BRL" },
            },
          ],
        },
      ],
      cdbs: [
        {
          id: "cdb-1",
          principal: { amount: "10000.00", currency: "BRL" },
          estimatedValue: { amount: "11250.00", currency: "BRL" },
          pnl: { amount: "1250.00", currency: "BRL" },
        },
      ],
    };
    const { api, request } = clientReturning(json(200, summary));

    await expect(api.getPortfolioSummary()).resolves.toEqual(summary);
    expect(request).toHaveBeenCalledWith("/api/v1/investments/summary");
  });

  it("cadastra lote de ação via POST /api/v1/investments/stocks com unitPrice em string de duas casas", async () => {
    const payload = {
      ticker: "PETR4",
      quantity: 100,
      unitPrice: { amount: "10.00", currency: "BRL" as const },
      purchaseDate: "2026-01-15",
    };
    const created = {
      id: "lot-1",
      ticker: "PETR4",
      quantity: 100,
      unitPrice: { amount: "10.00", currency: "BRL" as const },
      acquisitionCost: { amount: "1000.00", currency: "BRL" as const },
      purchaseDate: "2026-01-15",
      createdAt: "2026-01-15T12:00:00Z",
      currentQuote: null,
      currentValue: null,
    };
    const { api, request } = clientReturning(json(201, created));

    await expect(api.registerStockLot(payload)).resolves.toEqual(created);
    expect(request).toHaveBeenCalledWith("/api/v1/investments/stocks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  });

  it("exclui lote de ação via DELETE /api/v1/investments/stocks/{id}", async () => {
    const { api, request } = clientReturning(new Response(null, { status: 204 }));

    await api.deleteStockLot("lot-1");

    expect(request).toHaveBeenCalledWith("/api/v1/investments/stocks/lot-1", { method: "DELETE" });
  });

  it("cadastra CDB via POST /api/v1/investments/cdbs com principal em string de duas casas e taxa decimal", async () => {
    const payload = {
      principal: { amount: "10000.00", currency: "BRL" as const },
      annualRate: "0.1250",
      startDate: "2026-01-01",
      maturityDate: "2027-01-01",
    };
    const created = {
      id: "cdb-1",
      principal: { amount: "10000.00", currency: "BRL" as const },
      annualRate: "0.1250",
      startDate: "2026-01-01",
      maturityDate: "2027-01-01",
      estimatedValue: { amount: "10100.00", currency: "BRL" as const },
      createdAt: "2026-01-01T12:00:00Z",
    };
    const { api, request } = clientReturning(json(201, created));

    await expect(api.registerCdb(payload)).resolves.toEqual(created);
    expect(request).toHaveBeenCalledWith("/api/v1/investments/cdbs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  });

  it("exclui CDB via DELETE /api/v1/investments/cdbs/{id}", async () => {
    const { api, request } = clientReturning(new Response(null, { status: 204 }));

    await api.deleteCdb("cdb-1");

    expect(request).toHaveBeenCalledWith("/api/v1/investments/cdbs/cdb-1", { method: "DELETE" });
  });

  it("propaga ApiError 400 quando o cadastro de ação falha na validação", async () => {
    const { api } = clientReturning(
      json(400, { code: "INVALID_REQUEST", detail: "Ticker B3 inválido", title: "Requisição inválida" }),
    );

    const failure = await api
      .registerStockLot({
        ticker: "INVALIDO",
        quantity: 10,
        unitPrice: { amount: "10.00", currency: "BRL" },
        purchaseDate: "2026-01-15",
      })
      .catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).status).toBe(400);
    expect((failure as ApiError).detail).toBe("Ticker B3 inválido");
  });
});
