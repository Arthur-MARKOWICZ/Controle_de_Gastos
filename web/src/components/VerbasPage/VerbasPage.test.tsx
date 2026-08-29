import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { VerbasPage } from "./VerbasPage";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: () => null }),
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: { request: vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve([]), status: 200 })) }, state: "authenticated" }),
}));

vi.mock("../../hooks/useEnvelopes", () => ({
  useEnvelopes: () => ({
    envelopes: [
      { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
      { id: "2", name: "Aluguel", purpose: "FIXED", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "0.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
      { id: "3", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "2000.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
      { id: "4", name: "Lazer", purpose: "LIMIT", baseAmount: { amount: "300.00", currency: "BRL" }, available: { amount: "-50.00", currency: "BRL" }, isNegative: true, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    ],
    loading: false,
    error: null,
    refresh: vi.fn(),
    setEnvelopes: vi.fn(),
  }),
}));

vi.mock("../../hooks/useLedgerSummary", () => ({
  useLedgerSummary: () => ({
    summary: {
      income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
      allocated: { amount: "4200.00", currency: "BRL" },
      unallocated: { amount: "800.00", currency: "BRL" },
      usagePct: 84,
      envelopes: [
        { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
        { id: "2", name: "Aluguel", purpose: "FIXED", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "0.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
        { id: "3", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "2000.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
        { id: "4", name: "Lazer", purpose: "LIMIT", baseAmount: { amount: "300.00", currency: "BRL" }, available: { amount: "-50.00", currency: "BRL" }, isNegative: true, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
      ],
    },
    loading: false,
    error: null,
    refresh: vi.fn(),
    setSummary: vi.fn(),
  }),
}));

afterEach(cleanup);

describe("VerbasPage", () => {
  it("renderiza um canto para cada tipo de verba", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getByRole("heading", { level: 1, name: "Verbas" })).toBeDefined();
    expect(screen.getByRole("heading", { level: 2, name: /Limite de gasto/ })).toBeDefined();
    expect(screen.getByRole("heading", { level: 2, name: /Meta de aporte/ })).toBeDefined();
    expect(screen.getByRole("heading", { level: 2, name: /Compromisso fixo/ })).toBeDefined();
  });

  it("expõe barra de progresso acessível para cada verba", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getAllByRole("progressbar")).toHaveLength(4);
    expect(screen.getByRole("progressbar", { name: "Progresso de Combustível" }).getAttribute("value")).toBe("60");
  });

  it("mostra alerta para verbas negativas e faixa não alocado", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Não alocado/).length).toBeGreaterThan(0);
  });

  it("expõe tabs filtráveis e busca", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getByRole("tab", { name: /Todas/ })).toBeDefined();
    expect(screen.getByLabelText(/Buscar verba/)).toBeDefined();
  });
});
