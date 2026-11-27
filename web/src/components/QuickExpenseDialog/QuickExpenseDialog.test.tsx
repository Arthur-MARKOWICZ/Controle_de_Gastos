import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { EnvelopeDTO } from "../../lib/api";
import { QuickExpenseDialog } from "./QuickExpenseDialog";

const createEntry = vi.fn();
const getExpenseSuggestions = vi.fn();

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: {} }),
}));

vi.mock("../../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...original,
    createApiClient: () => ({
      createEntry,
      getLedgerSummary: vi.fn(),
      getExpenseSuggestions,
    }),
  };
});

beforeEach(() => {
  createEntry.mockReset();
  getExpenseSuggestions.mockReset();
  getExpenseSuggestions.mockResolvedValue({ asOf: "2026-11-27T12:00:00-03:00", items: [] });
});

afterEach(() => {
  cleanup();
});

const envelopes: EnvelopeDTO[] = [
  {
    id: "env-1",
    ownerId: "u-1",
    name: "Mercado",
    purpose: "LIMIT",
    baseAmount: { amount: "500.00", currency: "BRL" },
    targetAmount: null,
    targetReachedAt: null,
    available: { amount: "100.00", currency: "BRL" },
    isNegative: false,
    role: "OWNER",
    wealthAllocation: false,
    createdAt: "2026-11-01T00:00:00Z",
    archivedAt: null,
    version: 0,
  },
  {
    id: "env-2",
    ownerId: "u-1",
    name: "Reserva",
    purpose: "GOAL",
    baseAmount: { amount: "1000.00", currency: "BRL" },
    targetAmount: null,
    targetReachedAt: null,
    available: { amount: "600.00", currency: "BRL" },
    isNegative: false,
    role: "OWNER",
    wealthAllocation: true,
    createdAt: "2026-11-01T00:00:00Z",
    archivedAt: null,
    version: 0,
  },
];

describe("QuickExpenseDialog", () => {
  it("registra gasto em uma única etapa com projeção de saldo negativo", async () => {
    createEntry.mockResolvedValueOnce({ id: "e-1", targetJustReached: false });
    const onSuccess = vi.fn();

    render(
      <QuickExpenseDialog
        open
        envelopes={envelopes}
        onClose={vi.fn()}
        onSuccess={onSuccess}
      />,
    );

    fireEvent.change(screen.getByLabelText("Valor (BRL)"), { target: { value: "12000" } });
    expect(screen.getByText(/Saldo após o gasto/i).textContent).toMatch(/-R\$\s*20,00/);
    expect(screen.getByText(/saldo negativo/i)).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "Registrar gasto" }));

    await waitFor(() => expect(createEntry).toHaveBeenCalledTimes(1));
    expect(createEntry).toHaveBeenCalledWith(
      "env-1",
      expect.objectContaining({
        kind: "EXPENSE",
        amount: { amount: "120.00", currency: "BRL" },
      }),
    );
    expect(onSuccess).toHaveBeenCalledWith(
      expect.objectContaining({ entryId: "e-1", envelopeName: "Mercado", kind: "EXPENSE" }),
    );
  });

  it("alterna para aporte ao escolher uma meta pelo chip", async () => {
    createEntry.mockResolvedValueOnce({ id: "e-2", targetJustReached: true });
    render(
      <QuickExpenseDialog
        open
        envelopes={envelopes}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /Reserva/i }));
    expect(screen.getByRole("button", { name: "Registrar aporte" })).toBeDefined();
  });

  it("pré-seleciona verba e valor da primeira sugestão sem enviar gasto", async () => {
    getExpenseSuggestions.mockResolvedValueOnce({
      asOf: "2026-11-27T12:00:00-03:00",
      items: [{ envelopeId: "env-1", name: "Mercado", suggestedAmount: { amount: "32.50", currency: "BRL" } }],
    });
    const onSuccess = vi.fn();

    render(
      <QuickExpenseDialog open envelopes={envelopes} onClose={vi.fn()} onSuccess={onSuccess} />,
    );

    await waitFor(() => {
      expect((screen.getByLabelText("Verba") as HTMLSelectElement).value).toBe("env-1");
      expect((screen.getByLabelText("Valor (BRL)") as HTMLInputElement).value).toBe("32,50");
    });
    expect(createEntry).not.toHaveBeenCalled();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("com suggestedAmount nulo seleciona a verba e deixa o valor vazio", async () => {
    getExpenseSuggestions.mockResolvedValueOnce({
      asOf: "2026-11-27T12:00:00-03:00",
      items: [{ envelopeId: "env-1", name: "Mercado", suggestedAmount: null }],
    });

    render(
      <QuickExpenseDialog open envelopes={envelopes} onClose={vi.fn()} onSuccess={vi.fn()} />,
    );

    await waitFor(() => {
      expect((screen.getByLabelText("Verba") as HTMLSelectElement).value).toBe("env-1");
    });
    expect((screen.getByLabelText("Valor (BRL)") as HTMLInputElement).value).toBe("");
  });

  it("com lista vazia mantém a heurística da primeira verba spendable", async () => {
    getExpenseSuggestions.mockResolvedValueOnce({ asOf: "2026-11-27T12:00:00-03:00", items: [] });

    render(
      <QuickExpenseDialog open envelopes={envelopes} onClose={vi.fn()} onSuccess={vi.fn()} />,
    );

    await waitFor(() => expect(getExpenseSuggestions).toHaveBeenCalled());
    expect((screen.getByLabelText("Verba") as HTMLSelectElement).value).toBe("env-1");
    expect((screen.getByLabelText("Valor (BRL)") as HTMLInputElement).value).toBe("");
  });
});
