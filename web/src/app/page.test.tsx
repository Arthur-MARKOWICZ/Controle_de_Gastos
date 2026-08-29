import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "./dashboard";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: () => null }),
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("../auth/auth-context", () => ({
  useAuth: () => ({ client: { request: vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve([]), status: 200 })) }, state: "anonymous" }),
}));

vi.mock("../hooks/useEnvelopes", () => ({
  useEnvelopes: () => ({
    envelopes: [
      { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER" },
      { id: "2", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER" },
      { id: "3", name: "Livros", purpose: "GOAL", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "180.00", currency: "BRL" }, isNegative: false, role: "OWNER" },
    ],
    loading: false,
    error: null,
    refresh: vi.fn(),
    setEnvelopes: vi.fn(),
  }),
}));

vi.mock("../hooks/useLedgerSummary", () => ({
  useLedgerSummary: () => ({
    summary: {
      income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
      allocated: { amount: "4250.00", currency: "BRL" },
      unallocated: { amount: "750.00", currency: "BRL" },
      usagePct: 85,
      envelopes: [
        { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
        { id: "2", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
        { id: "3", name: "Livros", purpose: "GOAL", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "180.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
      ],
    },
    loading: false,
    error: null,
    refresh: vi.fn(),
    setSummary: vi.fn(),
  }),
}));

vi.mock("../hooks/useIncome", () => ({
  useIncome: () => ({
    income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
    loading: false,
    error: null,
    notConfigured: false,
    refresh: vi.fn(),
    setIncome: vi.fn(),
  }),
  useIncomeHistory: () => ({
    data: { items: [{ id: "h1", amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString(), changedBy: "user-1" }], page: 0, size: 20, hasNext: false },
    loading: false,
    error: null,
    page: 0,
    load: vi.fn(),
    refresh: vi.fn(),
  }),
}));

afterEach(cleanup);

describe("Dashboard", () => {
  it("identifica a visão geral e expõe a conta autenticada", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getByRole("heading", { level: 1, name: "Visão geral" })).toBeDefined();
    expect(screen.getByText("pessoa@example.com")).toBeDefined();
    expect(screen.getByRole("button", { name: "Sair da conta" })).toBeDefined();
  });

  it("expõe um indicador de progresso acessível para cada verba", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getAllByRole("progressbar")).toHaveLength(3);
    expect(screen.getByRole("progressbar", { name: "Progresso de Combustível" })
      .getAttribute("value")).toBe("60");
  });
});
