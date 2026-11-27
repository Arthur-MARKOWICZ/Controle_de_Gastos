import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../ui/ToastProvider";
import { HistoryPage } from "./HistoryPage";
import { currentMonthSaoPaulo, todaySaoPaulo } from "../../lib/dates";

function renderPage() {
  return render(
    <ToastProvider>
      <HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />
    </ToastProvider>,
  );
}

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
      kind: "EXPENSE",
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
  matchedTotal: "50.00",
}));

function resetApiState() {
  apiState.items = [ownerItem()];
  apiState.envelopes = [ownerEnvelope()];
  apiState.purposeTotals = [];
  apiState.matchedTotal = "50.00";
  navState.query = "from=2026-08-01&to=2026-08-31";
  navState.push = vi.fn();
  getHistory.fn = vi.fn().mockImplementation(() =>
    Promise.resolve({
      items: apiState.items,
      page: 0,
      size: 10,
      hasNext: false,
      matchedTotal: { amount: apiState.matchedTotal, currency: "BRL" },
    }),
  );
}

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

const navState = vi.hoisted(() => ({
  query: "from=2026-08-01&to=2026-08-31",
  push: (() => undefined) as (url: string) => void,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navState.push }),
  useSearchParams: () => new URLSearchParams(navState.query),
}));

const getHistory = vi.hoisted(() => ({ fn: (() => undefined) as unknown as ReturnType<typeof vi.fn> }));

const authClient = vi.hoisted(() => ({ request: vi.fn() }));

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: authClient }),
}));

vi.mock("../../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...original,
    createApiClient: () => ({
      getHistory: getHistory.fn,
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
      getExpenseSuggestions: vi.fn().mockResolvedValue({ asOf: "2026-11-27T12:00:00-03:00", items: [] }),
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
    expect(screen.getByText("Combustível", { selector: "strong" })).toBeDefined();
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
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Excluir" }));
    const dialog = screen.getByRole("dialog", { name: "Excluir lançamento?" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleteHistoryEntry).toHaveBeenCalledWith("entry-1"));
    expect(await screen.findAllByText("Gasto excluído.")).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Desfazer" })).toBeDefined();
  });

  it("desfaz exclusão recriando o gasto", async () => {
    deleteHistoryEntry.mockResolvedValueOnce(undefined);
    createEntry.mockResolvedValueOnce({ id: "entry-restored" });
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Excluir" }));
    fireEvent.click(within(screen.getByRole("dialog", { name: "Excluir lançamento?" })).getByRole("button", { name: "Excluir" }));

    fireEvent.click(await screen.findByRole("button", { name: "Desfazer" }));
    await waitFor(() =>
      expect(createEntry).toHaveBeenCalledWith(
        "env-1",
        expect.objectContaining({
          kind: "EXPENSE",
          amount: { amount: "50.00", currency: "BRL" },
          occurredAt: "2026-08-10",
          description: "Gasolina",
        }),
      ),
    );
    expect(await screen.findByText("Exclusão desfeita.")).toBeDefined();
  });

  it("marca o chip de período selecionado com aria-pressed", async () => {
    const today = todaySaoPaulo();
    const month = currentMonthSaoPaulo();
    navState.query = `from=${month}-01&to=${today}`;
    renderPage();

    const thisMonth = await screen.findByRole("button", { name: "Este mês" });
    expect(thisMonth.getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "Mês passado" }).getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: "Últimos 7 dias" }).getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: "Personalizado" }).getAttribute("aria-pressed")).toBe("false");
  });

  it("marca Personalizado com aria-pressed quando o período não é um preset", async () => {
    navState.query = "from=2026-01-01&to=2026-01-15";
    renderPage();

    expect((await screen.findByRole("button", { name: "Personalizado" })).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "Este mês" }).getAttribute("aria-pressed")).toBe("false");
  });

  it("abre os filtros de data ao acionar o chip Personalizado", async () => {
    const today = todaySaoPaulo();
    const month = currentMonthSaoPaulo();
    navState.query = `from=${month}-01&to=${today}`;
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Personalizado" }));

    expect(screen.getByLabelText("De")).toBeDefined();
    expect(screen.getByLabelText("Até")).toBeDefined();
  });

  it("se chama Histórico e oferece registrar gasto no cabeçalho", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Histórico" })).toBeDefined();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    // cabeçalho + CTA do shell compartilham o rótulo aprovado em DESIGN.md
    expect((await screen.findAllByRole("button", { name: "Registrar gasto" })).length).toBeGreaterThan(0);
  });

  it("abre o diálogo unificado de lançamento rápido permitindo escolher a verba e o valor", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.click((await screen.findAllByRole("button", { name: "Registrar gasto" }))[0]);

    const dialog = screen.getByRole("dialog", { name: "Lançamento rápido" });
    expect(within(dialog).getByLabelText("Verba")).toBeDefined();
    expect(within(dialog).getByLabelText("Valor (BRL)")).toBeDefined();
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

    expect(await screen.findByText("Combustível", { selector: "strong" })).toBeDefined();
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

  describe("busca e filtros", () => {
    function secondEnvelope() {
      return { ...ownerEnvelope(), id: "env-2", name: "Saúde" };
    }

    it("lê busca, verba e autor da URL e envia para a API", async () => {
      navState.query = "from=2026-08-01&to=2026-08-31&q=farm%C3%A1cia&envelopeId=env-2&author=ME";
      apiState.envelopes = [ownerEnvelope(), secondEnvelope()];
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

      await screen.findByText("Combustível", { selector: "strong" });
      expect(getHistory.fn).toHaveBeenCalledWith("2026-08-01", "2026-08-31", 0, 10, false, {
        q: "farmácia",
        envelopeId: "env-2",
        author: "ME",
      });
      expect((screen.getByLabelText("Buscar na descrição") as HTMLInputElement).value).toBe("farmácia");
      expect((screen.getByLabelText("Verba") as HTMLSelectElement).value).toBe("env-2");
      expect((screen.getByLabelText("Autor") as HTMLSelectElement).value).toBe("ME");
    });

    it("aplica a busca ao confirmar, volta para a primeira página e omite filtros vazios", async () => {
      navState.query = "from=2026-08-01&to=2026-08-31&page=2";
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);
      const input = await screen.findByLabelText("Buscar na descrição");

      fireEvent.change(input, { target: { value: "  farmácia " } });
      fireEvent.submit(input.closest("form") as HTMLFormElement);

      expect(navState.push).toHaveBeenLastCalledWith("/gastos?from=2026-08-01&to=2026-08-31&page=0&q=farm%C3%A1cia");
    });

    it("aplica a busca após uma pausa na digitação, não a cada tecla", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      try {
        render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);
        const input = await screen.findByLabelText("Buscar na descrição");
        fireEvent.change(input, { target: { value: "f" } });
        fireEvent.change(input, { target: { value: "fa" } });
        expect(navState.push).not.toHaveBeenCalled();
        vi.advanceTimersByTime(450);
        expect(navState.push).toHaveBeenCalledTimes(1);
        expect(navState.push).toHaveBeenLastCalledWith("/gastos?from=2026-08-01&to=2026-08-31&page=0&q=fa");
      } finally {
        vi.useRealTimers();
      }
    });

    it("filtra por verba e por autor pela URL", async () => {
      apiState.envelopes = [ownerEnvelope(), secondEnvelope()];
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);
      fireEvent.click(screen.getByRole("button", { name: "Filtros" }));
      const envelopeSelect = await screen.findByLabelText("Verba");
      await screen.findByRole("option", { name: "Saúde" });

      fireEvent.change(envelopeSelect, { target: { value: "env-2" } });
      expect(navState.push).toHaveBeenLastCalledWith("/gastos?from=2026-08-01&to=2026-08-31&page=0&envelopeId=env-2");

      fireEvent.change(screen.getByLabelText("Autor"), { target: { value: "OTHERS" } });
      expect(navState.push).toHaveBeenLastCalledWith("/gastos?from=2026-08-01&to=2026-08-31&page=0&author=OTHERS");
      expect(screen.getByRole("option", { name: "Lançados por mim" })).toBeDefined();
      expect(screen.getByRole("option", { name: "Lançados por outras pessoas" })).toBeDefined();
    });

    it("limpar filtros remove busca, verba e autor da URL", async () => {
      navState.query = "from=2026-08-01&to=2026-08-31&q=farm&envelopeId=env-1&author=ME&includeDeleted=true";
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

      fireEvent.click(await screen.findByRole("button", { name: "Limpar filtros" }));

      expect(navState.push).toHaveBeenLastCalledWith("/gastos?from=2026-08-01&to=2026-08-31&page=0&includeDeleted=true");
    });

    it("mostra o total dos resultados só com filtro ativo", async () => {
      navState.query = "from=2026-08-01&to=2026-08-31&q=gasolina";
      apiState.matchedTotal = "48.00";
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

      const total = await screen.findByText(/Total dos resultados/);
      expect(total.textContent).toMatch(/R\$\s?48,00/);
      expect(total.textContent).toMatch(/todas as páginas/);
      expect(total.textContent).toMatch(/sem excluídos/);
    });

    it("não mostra o total filtrado sem filtro", async () => {
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);
      await screen.findByText("Combustível", { selector: "strong" });
      expect(screen.queryByText(/Total dos resultados/)).toBeNull();
      expect(screen.queryByRole("button", { name: "Limpar filtros" })).toBeNull();
    });

    it("distingue busca sem resultado do período vazio", async () => {
      navState.query = "from=2026-08-01&to=2026-08-31&q=inexistente";
      apiState.items = [];
      apiState.matchedTotal = "0.00";
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

      expect(await screen.findByText("Nenhum gasto corresponde aos filtros.")).toBeDefined();
      expect(screen.queryByText("Nenhum gasto no período selecionado.")).toBeNull();
      const empty = screen.getByText("Nenhum gasto corresponde aos filtros.").parentElement as HTMLElement;
      expect(within(empty).getByRole("button", { name: "Limpar filtros" })).toBeDefined();
    });

    it("participante continua sem editar nem excluir nos resultados filtrados", async () => {
      navState.query = "from=2026-08-01&to=2026-08-31&author=ME";
      apiState.items = [{ ...ownerItem(), role: "PARTICIPANT" }];
      render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

      await screen.findByText("Combustível", { selector: "strong" });
      expect(screen.queryByRole("button", { name: "Editar" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Excluir" })).toBeNull();
    });
  });
});
