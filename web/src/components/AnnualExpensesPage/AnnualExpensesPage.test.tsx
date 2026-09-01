import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AnnualExpensesPage } from "./AnnualExpensesPage";

vi.mock("../../auth/auth-context", () => ({ useAuth: () => ({ client: { request: vi.fn() } }) }));
vi.mock("../../hooks/useLedgerSummary", () => ({ useLedgerSummary: () => ({
  summary: { income: null, allocated: { amount: "200.00", currency: "BRL" }, unallocated: { amount: "0.00", currency: "BRL" }, usagePct: 0, envelopes: [{
    id: "ipva", ownerId: "owner", name: "IPVA", purpose: "ANNUAL_EXPENSE", baseAmount: { amount: "0.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
    annualExpense: { annualAmount: { amount: "1000.00", currency: "BRL" }, dueMonth: 1, dueDay: 10, fundingMode: "MONTHLY" }, available: { amount: "400.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
  }] }, loading: false, error: null, refresh: vi.fn(),
}) }));

afterEach(cleanup);

describe("AnnualExpensesPage", () => {
  it("presents annual expenses with their due date and a creation action", () => {
    render(<AnnualExpensesPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getByRole("heading", { level: 1, name: "Gastos anuais" })).toBeDefined();
    expect(screen.getByText(/vencimento em 10\/01/)).toBeDefined();
    expect(screen.getByRole("button", { name: "Novo gasto anual" })).toBeDefined();
  });
});
