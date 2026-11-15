import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "./Dashboard";

const request = vi.hoisted(() => vi.fn());

const alertsState = vi.hoisted(() => ({
  items: [] as Array<{ id: string; envelopeId: string; envelopeName: string; referenceMonth: string }>,
}));

const pendingState = vi.hoisted(() => ({
  current: [] as Array<Record<string, unknown>>,
  confirm: null as null | ((id: string) => Promise<unknown>),
}));

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
    { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "2", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "3", name: "Livros", purpose: "GOAL", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "180.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
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
  alertsState.items = [];
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

vi.mock("../../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...original,
    // mantém o cliente real (createEntry etc.) e só encena os alertas persistentes
    createApiClient: (client: Parameters<typeof original.createApiClient>[0]) => ({
      ...original.createApiClient(client),
      listAlerts: () => Promise.resolve({ items: alertsState.items }),
      listPendingOccurrences: () => Promise.resolve({ current: pendingState.current, overdue: [] }),
      confirmOccurrence: (id: string) => pendingState.confirm!(id),
      dismissAlert: (id: string) => {
        alertsState.items = alertsState.items.filter((alert) => alert.id !== id);
        return Promise.resolve();
      },
    }),
  };
});

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
beforeEach(() => {
  pendingState.current = [];
  pendingState.confirm = () => Promise.resolve({ id: "o1", status: "CONFIRMED", ledgerEntryId: "l1" });
});

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
    // LIMIT enche conforme o mês é gasto: 60% disponível = 40% usado
    expect(screen.getByRole("progressbar", { name: "Progresso de Combustível" })
      .getAttribute("value")).toBe("40");
  });

  it("abre o glossário de verbas explicando os tipos de verba", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Ver glossário de verbas" }));

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

  it("mostra a renda uma única vez, com reservado, não alocado e uso", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    const summary = screen.getByRole("region", { name: /Resumo do mês/i });
    expect(within(summary).getAllByText(/R\$\s*5\.000,00/)).toHaveLength(1);
    expect(within(summary).getByText(/R\$\s*4\.250,00/)).toBeDefined();
    expect(within(summary).getByText(/R\$\s*750,00/)).toBeDefined();
    expect(within(summary).getByText("85%")).toBeDefined();
    expect(within(summary).getByRole("button", { name: /renda/i })).toBeDefined();

    // a renda não aparece como figura principal de um segundo card
    expect(document.querySelectorAll("section[data-summary]")).toHaveLength(1);
    expect(screen.queryByRole("heading", { name: "Renda do mês" })).toBeNull();
  });

  it("não repete Nova verba no rodapé nem traz Glossário como ação de header", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getAllByRole("button", { name: "Nova verba" })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: "Glossário de verbas" })).toBeNull();
    expect(screen.getByRole("button", { name: /glossário/i })).toBeDefined();
  });

  it("limita a prévia a quatro verbas e mantém o link para ver todas", () => {
    ledgerHook.summary = {
      ...happyLedgerSummary(),
      envelopes: [
        ...summaryEnvelopes(),
        { id: "4", name: "Lazer", purpose: "LIMIT", baseAmount: { amount: "200.00", currency: "BRL" }, available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
        { id: "5", name: "Farmácia", purpose: "LIMIT", baseAmount: { amount: "150.00", currency: "BRL" }, available: { amount: "150.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
      ],
    };

    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    const preview = screen.getByRole("region", { name: "Suas verbas" });
    expect(within(preview).getAllByRole("progressbar")).toHaveLength(4);
    expect(within(preview).queryByRole("heading", { name: "Farmácia" })).toBeNull();
    expect(within(preview).getByRole("link", { name: /Ver todas/ })).toBeDefined();
  });

  it("oferece criar a primeira verba quando o mês está vazio", () => {
    ledgerHook.summary = { ...happyLedgerSummary(), envelopes: [] };

    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getByText(/Nenhuma verba neste mês/)).toBeDefined();
    expect(screen.getByRole("button", { name: "Criar primeira verba" })).toBeDefined();
  });

  it("mostra e dispensa alertas persistentes de saldo negativo", async () => {
    alertsState.items = [{ id: "a1", envelopeId: "1", envelopeName: "Combustível", referenceMonth: "2026-08" }];

    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    const alerts = await screen.findByRole("region", { name: "Alertas persistentes de saldo negativo" });
    expect(within(alerts).getByText("Combustível")).toBeDefined();
    expect(within(alerts).getByText(/2026-08/)).toBeDefined();

    fireEvent.click(within(alerts).getByRole("button", { name: /dispensar|fechar/i }));

    await waitFor(() =>
      expect(screen.queryByRole("region", { name: "Alertas persistentes de saldo negativo" })).toBeNull(),
    );
  });

  it("confirmar uma recorrência pendente recarrega verbas e resumo do mês", async () => {
    pendingState.current = [{
      id: "o1", recurringEntryId: "r1", envelopeId: "env-1", envelopeName: "Moradia", kind: "EXPENSE",
      description: "Aluguel", suggestedAmount: { amount: "1800.00", currency: "BRL" },
      dueDate: "2026-09-05", competence: "2026-09",
    }];
    const confirm = vi.fn().mockResolvedValue({ id: "o1", status: "CONFIRMED", ledgerEntryId: "l1" });
    pendingState.confirm = confirm;
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Confirmar Aluguel" }));

    await waitFor(() => expect(confirm).toHaveBeenCalledWith("o1"));
    await waitFor(() => {
      expect(ledgerHook.refresh).toHaveBeenCalled();
      expect(envelopesHook.refresh).toHaveBeenCalled();
    });
  });
});
