import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "./Dashboard";

const request = vi.hoisted(() => vi.fn());

const envelopesHook = vi.hoisted(() => ({
  envelopes: [] as Array<{
    id: string;
    name: string;
    purpose: string;
    baseAmount: { amount: string; currency: string };
    available: { amount: string; currency: string };
    isNegative: boolean;
    role: string;
  }>,
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
  setEnvelopes: vi.fn(),
}));

const ledgerHook = vi.hoisted(() => ({
  summary: null as {
    income: { amount: string; currency: string; effectiveFrom: string; changedAt: string };
    allocated: { amount: string; currency: string };
    unallocated: { amount: string; currency: string };
    usagePct: number;
    envelopes: Array<{
      id: string;
      name: string;
      purpose: string;
      baseAmount: { amount: string; currency: string };
      available: { amount: string; currency: string };
      isNegative: boolean;
      role: string;
      createdAt: string;
      archivedAt: null;
      version: number;
    }>;
  } | null,
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
  setSummary: vi.fn(),
}));

function listEnvelopes() {
  return [
    { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "400.00", currency: "BRL" }, isNegative: false, role: "OWNER" },
    { id: "2", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "1500.00", currency: "BRL" }, isNegative: false, role: "OWNER" },
    { id: "3", name: "Livros", purpose: "GOAL", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "400.00", currency: "BRL" }, isNegative: false, role: "OWNER" },
  ];
}

function summaryEnvelopes() {
  return [
    { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "2", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "3", name: "Livros", purpose: "GOAL", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "180.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
  ];
}

function happyLedgerSummary() {
  return {
    income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
    allocated: { amount: "4250.00", currency: "BRL" },
    unallocated: { amount: "750.00", currency: "BRL" },
    usagePct: 85,
    envelopes: summaryEnvelopes(),
  };
}

function resetHooks() {
  envelopesHook.envelopes = listEnvelopes();
  envelopesHook.loading = false;
  envelopesHook.error = null;
  envelopesHook.refresh = vi.fn();
  envelopesHook.setEnvelopes = vi.fn();
  ledgerHook.summary = happyLedgerSummary();
  ledgerHook.loading = false;
  ledgerHook.error = null;
  ledgerHook.refresh = vi.fn();
  ledgerHook.setSummary = vi.fn();
}

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
  useEnvelopes: () => envelopesHook,
}));

vi.mock("../../hooks/useLedgerSummary", () => ({
  useLedgerSummary: () => ledgerHook,
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

beforeEach(resetHooks);

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

  it("não mostra saldo de GET /envelopes quando o resumo do ledger falha", () => {
    ledgerHook.summary = null;
    ledgerHook.error = "Não foi possível carregar o resumo";

    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getByText("Não foi possível carregar o resumo")).toBeDefined();
    expect(screen.queryByRole("heading", { name: "Combustível" })).toBeNull();
    expect(screen.queryByText(/R\$\s*400,00 disponíveis/)).toBeNull();
    expect(screen.queryByRole("button", { name: "Registrar gasto em Combustível" })).toBeNull();
  });

  it("calcula a prévia do gasto com o saldo do resumo, não com GET /envelopes", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Registrar gasto em Combustível" }));
    const dialog = screen.getByRole("dialog", { name: "Registrar gasto em Combustível" });
    fireEvent.change(within(dialog).getByLabelText("Valor (BRL)"), { target: { value: "5000" } });

    expect(within(dialog).getByText(/Saldo após o gasto:\s*R\$\s*190,00/)).toBeDefined();
    expect(within(dialog).queryByText(/Saldo após o gasto:\s*R\$\s*350,00/)).toBeNull();
  });
});
