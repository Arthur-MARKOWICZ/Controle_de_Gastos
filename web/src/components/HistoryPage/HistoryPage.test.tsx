import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { HistoryPage } from "./HistoryPage";

const updateHistoryEntry = vi.fn();
const deleteHistoryEntry = vi.fn();
const createEntry = vi.fn();

function ownerItem() {
  return {
    purpose: "LIMIT",
    envelopeName: "Combustível",
    role: "OWNER",
    entry: {
      id: "entry-1",
      envelopeId: "env-1",
      amount: { amount: "50.00", currency: "BRL" },
      occurredAt: "2026-08-10",
      description: "Gasolina",
      deletedAt: null,
    },
  };
}

function ownerEnvelope() {
  return {
    id: "env-1",
    name: "Combustível",
    purpose: "LIMIT",
    baseAmount: { amount: "400.00", currency: "BRL" },
    available: { amount: "240.00", currency: "BRL" },
    isNegative: false,
    role: "OWNER",
    wealthAllocation: false,
    createdAt: "2026-08-01T00:00:00Z",
    archivedAt: null,
    version: 0,
  };
}

const apiState = vi.hoisted(() => ({
  items: [] as Array<Record<string, unknown>>,
  envelopes: [] as Array<Record<string, unknown>>,
  purposeTotals: [] as Array<{ purpose: string; amount: { amount: string; currency: string } }>,
}));

function resetApiState() {
  apiState.items = [ownerItem()];
  apiState.envelopes = [ownerEnvelope()];
  apiState.purposeTotals = [];
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
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams("from=2026-08-01&to=2026-08-31"),
}));

const authClient = vi.hoisted(() => ({ request: vi.fn() }));

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: authClient }),
}));

vi.mock("../../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...original,
    createApiClient: () => ({
      getHistory: vi.fn().mockImplementation(() =>
        Promise.resolve({ items: apiState.items, page: 0, size: 10, hasNext: false }),
      ),
      getHistorySummary: vi.fn().mockImplementation(() =>
        Promise.resolve({
          income: { amount: "5000.00", currency: "BRL" },
          expenses: { amount: "1200.00", currency: "BRL" },
          applied: { amount: "0.00", currency: "BRL" },
          netBalance: { amount: "3800.00", currency: "BRL" },
          accumulatedBalance: { amount: "2400.00", currency: "BRL" },
          monthlyTotals: [],
          purposeTotals: apiState.purposeTotals,
        }),
      ),
      getEnvelopes: vi.fn().mockImplementation(() => Promise.resolve(apiState.envelopes)),
      updateHistoryEntry,
      deleteHistoryEntry,
      createEntry,
      listEntryRevisions: vi.fn().mockResolvedValue({ items: [] }),
    }),
  };
});

beforeEach(resetApiState);

afterEach(() => {
  cleanup();
  updateHistoryEntry.mockReset();
  deleteHistoryEntry.mockReset();
  createEntry.mockReset();
});

describe("HistoryPage", () => {
  it("exibe os quatro indicadores e os gastos do intervalo filtrado", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Histórico" })).toBeDefined();
    expect(await screen.findByText("Renda do período")).toBeDefined();
    expect(screen.getByText("Consumo do período")).toBeDefined();
    expect(screen.getByText("Aplicado no período")).toBeDefined();
    expect(screen.getByText("Saldo líquido")).toBeDefined();
    expect(screen.getByText("Saldo acumulado")).toBeDefined();
    expect(screen.getByText("Combustível")).toBeDefined();
  });

  it("mostra a paginação a partir de 1 quando a API usa índice 0", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByText("Página 1")).toBeDefined();
    expect(screen.queryByText("Página 0")).toBeNull();
  });

  it("mostra erro de validação dentro do diálogo ao editar valor inválido", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Editar" }));
    const dialog = screen.getByRole("dialog", { name: "Editar gasto" });
    fireEvent.change(within(dialog).getByLabelText("Valor (BRL)"), { target: { value: "0" } });
    fireEvent.submit(within(dialog).getByLabelText("Valor (BRL)").closest("form")!);

    expect(await within(dialog).findByRole("alert")).toBeDefined();
    expect(updateHistoryEntry).not.toHaveBeenCalled();
  });

  it("mantém o diálogo aberto e mostra erro de API na edição", async () => {
    updateHistoryEntry.mockRejectedValueOnce(new Error("falha"));
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Editar" }));
    const dialog = screen.getByRole("dialog", { name: "Editar gasto" });
    fireEvent.change(within(dialog).getByLabelText("Valor (BRL)"), { target: { value: "7500" } });
    fireEvent.submit(within(dialog).getByLabelText("Valor (BRL)").closest("form")!);

    expect(await within(dialog).findByText(/não foi possível atualizar/i)).toBeDefined();
    expect(screen.getByRole("dialog", { name: "Editar gasto" })).toBeDefined();
  });

  it("confirma exclusão com diálogo nomeado", async () => {
    deleteHistoryEntry.mockResolvedValueOnce(undefined);
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Excluir" }));
    const dialog = screen.getByRole("dialog", { name: "Excluir lançamento?" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleteHistoryEntry).toHaveBeenCalledWith("entry-1"));
    expect(await screen.findByText("Gasto excluído.")).toBeDefined();
  });

  it("se chama Histórico e oferece registrar gasto no cabeçalho", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Histórico" })).toBeDefined();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    // o controle só existe depois que as verbas gastáveis carregam
    expect(await screen.findByRole("button", { name: "Registrar gasto" })).toBeDefined();
  });

  it("deixa o dono escolher a verba e abrir o formulário de gasto", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Registrar gasto" }));

    const picker = screen.getByRole("dialog", { name: "Registrar gasto" });
    fireEvent.click(within(picker).getByRole("button", { name: "Combustível" }));

    const form = await screen.findByRole("dialog", { name: "Registrar gasto em Combustível" });
    expect(within(form).getByLabelText("Valor (BRL)")).toBeDefined();
  });

  it("oferece registrar gasto também no estado vazio do período", async () => {
    apiState.items = [];
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByText(/Nenhum gasto no período selecionado/)).toBeDefined();
    const empty = screen.getByText(/Nenhum gasto no período selecionado/).closest("div, p, section")!;
    expect(within(empty.parentElement!).getAllByRole("button", { name: /Registrar gasto/ }).length).toBeGreaterThan(0);
  });

  it("não oferece editar nem excluir lançamento de outro dono ao participante", async () => {
    apiState.items = [{ ...ownerItem(), role: "PARTICIPANT" }];
    apiState.envelopes = [{ ...ownerEnvelope(), role: "PARTICIPANT" }];

    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByText("Combustível")).toBeDefined();
    expect(screen.getByText(/R\$\s*50,00/)).toBeDefined();
    expect(screen.queryByRole("button", { name: "Editar" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Excluir" })).toBeNull();
  });

  it("mostra a data do lançamento em pt-BR, não a string ISO", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByText(/10\/08\/2026/)).toBeDefined();
    expect(screen.queryByText(/2026-08-10/)).toBeNull();
  });

  it("dá a cada tipo um valor e uma barra proporcional em Gastos por tipo", async () => {
    apiState.purposeTotals = [
      { purpose: "LIMIT", amount: { amount: "1200.00", currency: "BRL" } },
      { purpose: "GOAL", amount: { amount: "300.00", currency: "BRL" } },
    ];

    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    const chart = await screen.findByRole("region", { name: "Gastos por tipo" });
    expect(within(chart).getByText(/R\$\s*1\.200,00/)).toBeDefined();
    expect(within(chart).getByText(/R\$\s*300,00/)).toBeDefined();

    const bars = chart.querySelectorAll("li i");
    expect(bars).toHaveLength(2);
    expect((bars[0] as HTMLElement).style.width).toBe("100%");
    expect((bars[1] as HTMLElement).style.width).toBe("25%");
  });
});
