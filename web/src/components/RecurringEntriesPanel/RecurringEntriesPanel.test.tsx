import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiClient, RecurringEntryDTO } from "../../lib/api";
import { RecurringEntriesPanel } from "./RecurringEntriesPanel";

afterEach(cleanup);

const rent: RecurringEntryDTO = {
  id: "r1", envelopeId: "e1", kind: "EXPENSE", amount: { amount: "1800.00", currency: "BRL" }, dayOfMonth: 5,
  description: "Aluguel", active: true, firstCompetence: "2026-12", version: 0,
};

function clientWith(items: RecurringEntryDTO[]) {
  return {
    listRecurringEntries: vi.fn().mockResolvedValue(items),
    createRecurringEntry: vi.fn().mockImplementation((_e: string, req: { amount: unknown; dayOfMonth: number; description: string | null }) =>
      Promise.resolve({ ...rent, id: "r2", ...req })),
    updateRecurringEntry: vi.fn().mockImplementation((_e: string, _id: string, req: object) => Promise.resolve({ ...rent, ...req })),
    deleteRecurringEntry: vi.fn().mockResolvedValue(undefined),
  } as unknown as ApiClient & Record<string, ReturnType<typeof vi.fn>>;
}

const envelope = { id: "e1", name: "Moradia", purpose: "FIXED" as const };

describe("RecurringEntriesPanel", () => {
  it("lista recorrências com valor e dia em texto", async () => {
    render(<RecurringEntriesPanel client={clientWith([rent])} envelope={envelope} onClose={vi.fn()} />);

    expect(await screen.findByText("Aluguel")).toBeDefined();
    expect(screen.getByText(/R\$\s?1\.800,00 · todo dia 5 · Ativa/)).toBeDefined();
  });

  it("cria recorrência de gasto com valor decimal", async () => {
    const client = clientWith([]);
    render(<RecurringEntriesPanel client={client} envelope={envelope} onClose={vi.fn()} />);
    await screen.findByText(/Nenhuma recorrência/);

    fireEvent.change(screen.getByLabelText("Descrição (opcional)"), { target: { value: "Aluguel" } });
    fireEvent.change(screen.getByLabelText("Valor sugerido (BRL)"), { target: { value: "180000" } });
    fireEvent.change(screen.getByLabelText("Dia do mês"), { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: "Adicionar recorrência" }));

    await waitFor(() => expect(client.createRecurringEntry).toHaveBeenCalledWith("e1", {
      kind: "EXPENSE", amount: { amount: "1800.00", currency: "BRL" }, dayOfMonth: 5, description: "Aluguel",
    }));
    expect(await screen.findByText("Aluguel")).toBeDefined();
  });

  it("usa aporte em metas", async () => {
    const client = clientWith([]);
    render(<RecurringEntriesPanel client={client} envelope={{ id: "g1", name: "Reserva", purpose: "GOAL" }} onClose={vi.fn()} />);
    await screen.findByText(/Nenhuma recorrência/);

    fireEvent.change(screen.getByLabelText("Valor sugerido (BRL)"), { target: { value: "50000" } });
    fireEvent.change(screen.getByLabelText("Dia do mês"), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: "Adicionar recorrência" }));

    await waitFor(() => expect(client.createRecurringEntry).toHaveBeenCalledWith("g1", expect.objectContaining({ kind: "CONTRIBUTION" })));
  });

  it("avisa que dias 29 a 31 viram o último dia em meses curtos", async () => {
    render(<RecurringEntriesPanel client={clientWith([])} envelope={envelope} onClose={vi.fn()} />);
    await screen.findByText(/Nenhuma recorrência/);

    fireEvent.change(screen.getByLabelText("Dia do mês"), { target: { value: "31" } });

    expect(screen.getByText(/meses mais curtos, a data será o último dia do mês/)).toBeDefined();
  });

  it("recusa dia fora de 1 a 31 sem chamar a API", async () => {
    const client = clientWith([]);
    render(<RecurringEntriesPanel client={client} envelope={envelope} onClose={vi.fn()} />);
    await screen.findByText(/Nenhuma recorrência/);

    fireEvent.change(screen.getByLabelText("Valor sugerido (BRL)"), { target: { value: "1000" } });
    fireEvent.change(screen.getByLabelText("Dia do mês"), { target: { value: "32" } });
    fireEvent.click(screen.getByRole("button", { name: "Adicionar recorrência" }));

    expect(await screen.findByText("Escolha um dia entre 1 e 31")).toBeDefined();
    expect(client.createRecurringEntry).not.toHaveBeenCalled();
  });

  it("desativa, edita e exclui com confirmação", async () => {
    const client = clientWith([rent]);
    render(<RecurringEntriesPanel client={client} envelope={envelope} onClose={vi.fn()} />);
    await screen.findByText("Aluguel");

    fireEvent.click(screen.getByRole("button", { name: "Desativar Aluguel" }));
    await waitFor(() => expect(client.updateRecurringEntry).toHaveBeenCalledWith("e1", "r1", expect.objectContaining({ active: false })));

    fireEvent.click(screen.getByRole("button", { name: "Editar Aluguel" }));
    fireEvent.change(screen.getByLabelText("Valor sugerido (BRL)"), { target: { value: "190000" } });
    fireEvent.click(screen.getByRole("button", { name: "Salvar alterações" }));
    await waitFor(() => expect(client.updateRecurringEntry).toHaveBeenLastCalledWith("e1", "r1",
      expect.objectContaining({ amount: { amount: "1900.00", currency: "BRL" } })));

    fireEvent.click(screen.getByRole("button", { name: "Excluir Aluguel" }));
    expect(client.deleteRecurringEntry).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar exclusão de Aluguel" }));
    await waitFor(() => expect(client.deleteRecurringEntry).toHaveBeenCalledWith("e1", "r1"));
    await waitFor(() => expect(screen.queryByText("Aluguel")).toBeNull());
  });

  it("mostra erro de carregamento", async () => {
    const client = { listRecurringEntries: vi.fn().mockRejectedValue(new Error("x")) } as unknown as ApiClient;
    render(<RecurringEntriesPanel client={client} envelope={envelope} onClose={vi.fn()} />);

    expect(await screen.findByText("Não foi possível carregar as recorrências.")).toBeDefined();
  });
});
