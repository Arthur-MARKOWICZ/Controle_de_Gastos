import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { Dashboard } from "./Dashboard";

const request = vi.hoisted(() => vi.fn());

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) { this.open = true; });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) { this.open = false; });
});

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: () => null }),
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: { request }, state: "anonymous" }),
}));

vi.mock("../../hooks/useEnvelopes", () => ({
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

vi.mock("../../hooks/useLedgerSummary", () => ({
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

vi.mock("../../hooks/useIncome", () => ({
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

afterEach(() => {
  cleanup();
  request.mockReset();
});

function respondWithEntry() {
  request.mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve({ id: "e1", targetJustReached: false }) });
}

async function submitAmount(dialogName: string, amount: string) {
  const dialog = screen.getByRole("dialog", { name: dialogName });
  fireEvent.change(within(dialog).getByLabelText("Valor (BRL)"), { target: { value: amount } });
  fireEvent.submit(within(dialog).getByLabelText("Valor (BRL)").closest("form")!);
  await waitFor(() => expect(request).toHaveBeenCalled());
  return JSON.parse(request.mock.calls[0][1].body);
}

describe("Dashboard", () => {
  it("registra aporte, e não gasto, ao aportar numa meta de aporte", async () => {
    respondWithEntry();
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Aportar em Investimentos" }));
    const body = await submitAmount("Aportar em Investimentos", "10000");

    expect(body.kind).toBe("CONTRIBUTION");
    expect(await screen.findByText('Aporte registrado em "Investimentos"')).toBeDefined();
  });

  it("continua registrando gasto em verba de limite", async () => {
    respondWithEntry();
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Registrar gasto em Combustível" }));
    const body = await submitAmount("Registrar gasto em Combustível", "5000");

    expect(body.kind).toBe("EXPENSE");
  });

  it("menciona o saldo negativo no aviso após um gasto acima do disponível", async () => {
    respondWithEntry();
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Registrar gasto em Combustível" }));
    await submitAmount("Registrar gasto em Combustível", "30000");

    expect(await screen.findByText(/Gasto registrado em "Combustível"\. A verba ficou com saldo negativo de -R\$\s*60,00/)).toBeDefined();
  });

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

  it("abre o glossário de verbas explicando os tipos de verba", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Glossário de verbas" }));

    const dialog = screen.getByRole("dialog", { name: "Glossário de verbas" });
    expect(within(dialog).getByText("Limite de gasto")).toBeDefined();
    expect(within(dialog).getByText("Meta de aporte")).toBeDefined();
  });
});
