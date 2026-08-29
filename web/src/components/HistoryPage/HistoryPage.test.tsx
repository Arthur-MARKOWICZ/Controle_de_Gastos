import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HistoryPage } from "./HistoryPage";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams("from=2026-08-01&to=2026-08-31"),
}));

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: { request: vi.fn() } }),
}));

vi.mock("../../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...original,
    createApiClient: () => ({
      getHistory: vi.fn().mockResolvedValue({ items: [], page: 0, size: 10, hasNext: false }),
      getHistorySummary: vi.fn().mockResolvedValue({
        income: { amount: "5000.00", currency: "BRL" }, expenses: { amount: "1200.00", currency: "BRL" },
        netBalance: { amount: "3800.00", currency: "BRL" }, accumulatedBalance: { amount: "2400.00", currency: "BRL" },
        monthlyTotals: [], purposeTotals: [],
      }),
      getEnvelopes: vi.fn().mockResolvedValue([]),
    }),
  };
});

afterEach(cleanup);

describe("HistoryPage", () => {
  it("exibe os quatro indicadores e um estado vazio para o intervalo filtrado", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Histórico" })).toBeDefined();
    expect(await screen.findByText("Renda do período")).toBeDefined();
    expect(screen.getByText("Gastos do período")).toBeDefined();
    expect(screen.getByText("Saldo líquido")).toBeDefined();
    expect(screen.getByText("Saldo acumulado")).toBeDefined();
    expect(screen.getByText("Nenhum gasto no período selecionado.")).toBeDefined();
  });
});
