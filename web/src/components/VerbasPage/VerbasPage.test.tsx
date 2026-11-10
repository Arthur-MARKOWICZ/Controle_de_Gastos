import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { VerbasPage } from "./VerbasPage";

const envelopesHook = vi.hoisted(() => ({
  envelopes: [] as Array<{
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
    { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "400.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "2", name: "Aluguel", purpose: "FIXED", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "1500.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "3", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "2000.00", currency: "BRL" }, available: { amount: "2000.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "4", name: "Lazer", purpose: "LIMIT", baseAmount: { amount: "300.00", currency: "BRL" }, available: { amount: "300.00", currency: "BRL" }, isNegative: true, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
  ];
}

function summaryEnvelopes() {
  return [
    { id: "1", name: "Combustível", purpose: "LIMIT", baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "2", name: "Aluguel", purpose: "FIXED", baseAmount: { amount: "1500.00", currency: "BRL" }, available: { amount: "0.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "3", name: "Investimentos", purpose: "GOAL", baseAmount: { amount: "2000.00", currency: "BRL" }, available: { amount: "1200.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
    { id: "4", name: "Lazer", purpose: "LIMIT", baseAmount: { amount: "300.00", currency: "BRL" }, available: { amount: "-50.00", currency: "BRL" }, isNegative: true, role: "OWNER", wealthAllocation: false, createdAt: new Date().toISOString(), archivedAt: null, version: 0 },
  ];
}

function happyLedgerSummary() {
  return {
    income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
    allocated: { amount: "4200.00", currency: "BRL" },
    unallocated: { amount: "800.00", currency: "BRL" },
    usagePct: 84,
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
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: () => null }),
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: { request: vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve([]), status: 200 })) }, state: "authenticated" }),
}));

vi.mock("../../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...original,
    createApiClient: () => ({
      archiveEnvelope: vi.fn().mockResolvedValue(undefined),
      listAlerts: vi.fn().mockResolvedValue({ items: [] }),
      dismissAlert: vi.fn().mockResolvedValue(undefined),
    }),
  };
});

vi.mock("../../hooks/useEnvelopes", () => ({
  useEnvelopes: () => envelopesHook,
}));

vi.mock("../../hooks/useLedgerSummary", () => ({
  useLedgerSummary: () => ledgerHook,
}));

beforeEach(resetHooks);

afterEach(cleanup);

describe("VerbasPage", () => {
  it("mantém limites e compromissos no orçamento mensal", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getByRole("heading", { level: 1, name: "Verbas" })).toBeDefined();
    expect(screen.getByRole("heading", { level: 2, name: /Limite de gasto/ })).toBeDefined();
    expect(screen.getByRole("heading", { level: 2, name: /Compromisso fixo/ })).toBeDefined();
  });

  it("expõe barra de progresso acessível para cada verba", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);
    expect(screen.getAllByRole("progressbar")).toHaveLength(3);
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

  it("abre o formulário de compromisso fixo já com a natureza FIXED", () => {
    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /Criar compromisso fixo/i }));
    const dialog = screen.getByRole("dialog", { name: "Nova verba" });
    expect(within(dialog).getByLabelText("Natureza")).toHaveProperty("value", "FIXED");
    expect(within(dialog).queryByRole("option", { name: "Meta de aporte" })).toBeNull();
  });

  it("não mostra saldo de GET /envelopes quando o resumo do ledger não está disponível", () => {
    ledgerHook.summary = null;
    ledgerHook.error = null;

    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.queryByRole("heading", { name: "Combustível" })).toBeNull();
    expect(screen.queryByRole("heading", { name: "Lazer" })).toBeNull();
    expect(screen.queryByText(/R\$\s*400,00 disponíveis/)).toBeNull();
    expect(screen.queryByRole("button", { name: "Registrar gasto em Combustível" })).toBeNull();
  });

  it("não mostra saldo de GET /envelopes quando o resumo do ledger falha", () => {
    ledgerHook.summary = null;
    ledgerHook.error = "Não foi possível carregar o resumo";

    render(<VerbasPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getByText("Não foi possível carregar o resumo")).toBeDefined();
    expect(screen.queryByRole("heading", { name: "Combustível" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Registrar gasto em Combustível" })).toBeNull();
  });
});
