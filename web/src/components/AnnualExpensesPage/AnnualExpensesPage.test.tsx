import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { EnvelopeDTO } from "../../lib/api";
import { ToastProvider } from "../ui/ToastProvider";
import { AnnualExpensesPage } from "./AnnualExpensesPage";

const archiveEnvelope = vi.fn();
const updateEnvelope = vi.fn();
const deleteHistoryEntry = vi.fn();
const refresh = vi.fn();
let ledger: { summary: unknown; loading: boolean; busy: boolean; error: string | null; refresh: typeof refresh };

vi.mock("../../auth/auth-context", () => ({ useAuth: () => ({ client: { request: vi.fn() } }) }));
vi.mock("../../hooks/useLedgerSummary", () => ({ useLedgerSummary: () => ledger }));
vi.mock("../../lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../lib/api")>()),
  createApiClient: () => ({ archiveEnvelope, updateEnvelope, deleteHistoryEntry }),
}));
vi.mock("../AppShell/AppShell", () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <main>{children}</main>,
}));
vi.mock("../ExpenseForm/ExpenseForm", async (importOriginal) => {
  const original = await importOriginal<typeof import("../ExpenseForm/ExpenseForm")>();
  return {
    ...original,
    ExpenseForm: ({
      onSuccess,
    }: {
      onSuccess(result: { entryId: string; targetJustReached: boolean; balanceAfter: string | null }): void;
    }) => (
      <form aria-label="Formulário de gasto">
        <button
          type="button"
          onClick={() => onSuccess({ entryId: "entry-1", targetJustReached: false, balanceAfter: null })}
        >
          Confirmar gasto mock
        </button>
      </form>
    ),
  };
});

const ipva = {
  id: "ipva", ownerId: "owner", name: "IPVA", purpose: "ANNUAL_EXPENSE",
  baseAmount: { amount: "0.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
  annualExpense: { annualAmount: { amount: "1000.00", currency: "BRL" }, dueMonth: 1, dueDay: 10, fundingMode: "MONTHLY" },
  available: { amount: "400.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false,
  createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 3,
} as unknown as EnvelopeDTO;

function summaryWith(envelopes: EnvelopeDTO[]) {
  return {
    income: null,
    allocated: { amount: "200.00", currency: "BRL" },
    unallocated: { amount: "0.00", currency: "BRL" },
    usagePct: 0,
    envelopes,
  };
}

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) { this.open = true; });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) { this.open = false; });
});

beforeEach(() => {
  ledger = { summary: summaryWith([ipva]), loading: false, busy: false, error: null, refresh };
});

afterEach(() => {
  cleanup();
  archiveEnvelope.mockReset();
  updateEnvelope.mockReset();
  deleteHistoryEntry.mockReset();
  refresh.mockReset();
});

function renderPage() {
  render(
    <ToastProvider>
      <AnnualExpensesPage email="pessoa@example.com" onLogout={vi.fn()} />
    </ToastProvider>,
  );
}

function openCardMenu(name = "IPVA") {
  fireEvent.click(screen.getByLabelText(`Mais ações de ${name}`));
}

function openEdit() {
  openCardMenu();
  fireEvent.click(screen.getByLabelText("Editar gasto anual IPVA"));
}

describe("AnnualExpensesPage", () => {
  it("mostra os gastos anuais com a data de vencimento e a ação de criar", () => {
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: "Gastos anuais" })).toBeDefined();
    expect(screen.getByText(/vencimento em 10\/01/)).toBeDefined();
    expect(screen.getByRole("button", { name: "Novo gasto anual" })).toBeDefined();
  });

  it("encerra o gasto anual depois da confirmação e recarrega o resumo", async () => {
    archiveEnvelope.mockResolvedValue(undefined);
    renderPage();

    openCardMenu();
    fireEvent.click(screen.getByLabelText("Encerrar IPVA"));
    fireEvent.click(screen.getByRole("button", { name: "Encerrar" }));

    expect(await screen.findByText('Gasto anual "IPVA" encerrado.')).toBeDefined();
    expect(archiveEnvelope).toHaveBeenCalledWith("ipva");
    expect(refresh).toHaveBeenCalled();
  });

  it("salva a edição com valor, vencimento, modo de reserva e a versão atual", async () => {
    updateEnvelope.mockResolvedValue(undefined);
    renderPage();

    openEdit();
    fireEvent.change(screen.getByLabelText("Valor anual (BRL)"), { target: { value: "1200,00" } });
    fireEvent.change(screen.getByLabelText("Mês"), { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Gasto anual atualizado.")).toBeDefined();
    expect(updateEnvelope).toHaveBeenCalledWith("ipva", {
      version: 3,
      annualAmount: { amount: "1200.00", currency: "BRL" },
      dueMonth: 3,
      dueDay: 10,
      fundingMode: "MONTHLY",
    });
  });

  it("cancelar a confirmação não encerra o gasto anual", () => {
    renderPage();

    openCardMenu();
    fireEvent.click(screen.getByLabelText("Encerrar IPVA"));
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(archiveEnvelope).not.toHaveBeenCalled();
    expect(screen.queryByText(/encerrado\./)).toBeNull();
  });

  it("participante não vê as ações de editar, encerrar nem reclassificar", () => {
    ledger.summary = summaryWith([{ ...ipva, role: "PARTICIPANT" } as EnvelopeDTO]);
    renderPage();

    expect(screen.queryByLabelText("Mais ações de IPVA")).toBeNull();
    expect(screen.queryByLabelText("Editar gasto anual IPVA")).toBeNull();
    expect(screen.queryByLabelText("Encerrar IPVA")).toBeNull();
    expect(screen.queryByLabelText("Marcar IPVA como alocação de patrimônio")).toBeNull();
  });

  it("não lista verbas de outro tipo na página de gastos anuais", () => {
    const mercado = { ...ipva, id: "mercado", name: "Mercado", purpose: "LIMIT", annualExpense: null } as unknown as EnvelopeDTO;
    ledger.summary = summaryWith([mercado]);
    renderPage();

    expect(screen.queryByText("Mercado")).toBeNull();
    expect(screen.getByText("Nenhum gasto anual criado ainda.")).toBeDefined();
  });

  it("valor anual zerado mostra o erro e não chama a API", async () => {
    renderPage();

    openEdit();
    fireEvent.change(screen.getByLabelText("Valor anual (BRL)"), { target: { value: "0,00" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Informe um valor anual positivo.")).toBeDefined();
    expect(updateEnvelope).not.toHaveBeenCalled();
  });

  it("falha ao salvar a edição mostra a mensagem e mantém o formulário aberto", async () => {
    updateEnvelope.mockRejectedValue(new Error("409"));
    renderPage();

    openEdit();
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Não foi possível atualizar o gasto anual.")).toBeDefined();
    expect(screen.getByLabelText("Valor anual (BRL)")).toBeDefined();
    expect(screen.queryByText("Gasto anual atualizado.")).toBeNull();
  });

  it("falha ao reclassificar mostra a mensagem e não recarrega", async () => {
    updateEnvelope.mockRejectedValue(new Error("500"));
    renderPage();

    openCardMenu();
    fireEvent.click(screen.getByLabelText("Marcar IPVA como alocação de patrimônio"));

    expect(await screen.findByText("Não foi possível atualizar a classificação do gasto anual")).toBeDefined();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("falha ao encerrar mostra a mensagem de erro e não anuncia o encerramento", async () => {
    archiveEnvelope.mockRejectedValue(new Error("403"));
    renderPage();

    openCardMenu();
    fireEvent.click(screen.getByLabelText("Encerrar IPVA"));
    fireEvent.click(screen.getByRole("button", { name: "Encerrar" }));

    expect(await screen.findByText("Não foi possível encerrar o gasto anual.")).toBeDefined();
    expect(screen.queryByText('Gasto anual "IPVA" encerrado.')).toBeNull();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("erro do resumo mostra a mensagem, esconde a lista e permite tentar novamente", () => {
    ledger.error = "Não foi possível carregar o resumo do mês.";
    renderPage();

    expect(screen.getByText("Não foi possível carregar o resumo do mês.")).toBeDefined();
    expect(screen.queryByText(/vencimento em/)).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(refresh).toHaveBeenCalledOnce();
  });

  it("ao registrar gasto mostra toast com Desfazer e remove o lançamento", async () => {
    deleteHistoryEntry.mockResolvedValueOnce(undefined);
    renderPage();

    fireEvent.click(screen.getByLabelText("Registrar gasto em IPVA"));
    fireEvent.click(screen.getByRole("button", { name: "Confirmar gasto mock" }));

    expect(await screen.findByRole("button", { name: "Desfazer" })).toBeDefined();
    expect(screen.getAllByText(/Gasto registrado/).length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: "Desfazer" }));
    await waitFor(() => expect(deleteHistoryEntry).toHaveBeenCalledWith("entry-1"));
    expect(await screen.findByText("Lançamento desfeito.")).toBeDefined();
  });
});

