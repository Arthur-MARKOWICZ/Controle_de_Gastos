import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { HistoryPage } from "./HistoryPage";

const updateHistoryEntry = vi.fn();
const deleteHistoryEntry = vi.fn();

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
      getHistory: vi.fn().mockResolvedValue({
        items: [
          {
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
          },
        ],
        page: 0,
        size: 10,
        hasNext: false,
      }),
      getHistorySummary: vi.fn().mockResolvedValue({
        income: { amount: "5000.00", currency: "BRL" },
        expenses: { amount: "1200.00", currency: "BRL" },
        netBalance: { amount: "3800.00", currency: "BRL" },
        accumulatedBalance: { amount: "2400.00", currency: "BRL" },
        monthlyTotals: [],
        purposeTotals: [],
      }),
      getEnvelopes: vi.fn().mockResolvedValue([
        {
          id: "env-1",
          name: "Combustível",
          purpose: "LIMIT",
          baseAmount: { amount: "400.00", currency: "BRL" },
          available: { amount: "240.00", currency: "BRL" },
          isNegative: false,
          role: "OWNER",
          createdAt: new Date().toISOString(),
          archivedAt: null,
          version: 0,
        },
      ]),
      updateHistoryEntry,
      deleteHistoryEntry,
    }),
  };
});

afterEach(() => {
  cleanup();
  updateHistoryEntry.mockReset();
  deleteHistoryEntry.mockReset();
});

describe("HistoryPage", () => {
  it("exibe os quatro indicadores e os gastos do intervalo filtrado", async () => {
    render(<HistoryPage email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Gastos" })).toBeDefined();
    expect(await screen.findByText("Renda do período")).toBeDefined();
    expect(screen.getByText("Gastos do período")).toBeDefined();
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
});
