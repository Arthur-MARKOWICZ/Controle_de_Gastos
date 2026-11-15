import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { ApiError, type ApiClient, type PendingOccurrenceDTO } from "../../lib/api";
import { PendingOccurrences } from "./PendingOccurrences";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

afterEach(cleanup);

function item(id: string, description: string, amount: string, dueDate: string, competence: string): PendingOccurrenceDTO {
  return { id, recurringEntryId: `r-${id}`, envelopeId: "e1", envelopeName: "Moradia", kind: "EXPENSE", description,
    suggestedAmount: { amount, currency: "BRL" }, dueDate, competence };
}

const rent = item("o1", "Aluguel", "1800.00", "2026-12-05", "2026-12");
const internet = item("o2", "Internet", "99.90", "2026-11-10", "2026-11");

function clientWith(current: PendingOccurrenceDTO[], overdue: PendingOccurrenceDTO[] = []) {
  return {
    listPendingOccurrences: vi.fn().mockResolvedValue({ current, overdue }),
    confirmOccurrence: vi.fn().mockResolvedValue({ id: "o1", status: "CONFIRMED", ledgerEntryId: "l1" }),
    discardOccurrence: vi.fn().mockResolvedValue(undefined),
  } as unknown as ApiClient & Record<string, ReturnType<typeof vi.fn>>;
}

describe("PendingOccurrences", () => {
  it("lista pendências do mês e vencidas com rótulo em texto", async () => {
    render(<PendingOccurrences client={clientWith([rent], [internet])} onChanged={vi.fn()} />);

    const month = await screen.findByRole("region", { name: "Pendências do mês" });
    expect(within(month).getByText("Aluguel")).toBeDefined();
    expect(within(month).getByText(/R\$\s?1\.800,00/)).toBeDefined();
    expect(within(month).getByText(/05\/12\/2026/)).toBeDefined();
    const overdue = screen.getByRole("region", { name: "Vencidas" });
    expect(within(overdue).getByText("Internet")).toBeDefined();
  });

  it("não ocupa espaço sem pendências", async () => {
    const client = clientWith([]);
    const { container } = render(<PendingOccurrences client={client} onChanged={vi.fn()} />);

    await waitFor(() => expect(client.listPendingOccurrences).toHaveBeenCalled());
    expect(container.innerHTML).toBe("");
  });

  it("confirma com o valor sugerido e avisa para recarregar saldos", async () => {
    const client = clientWith([rent]);
    const onChanged = vi.fn();
    render(<PendingOccurrences client={client} onChanged={onChanged} />);

    fireEvent.click(await screen.findByRole("button", { name: "Confirmar Aluguel" }));

    await waitFor(() => expect(client.confirmOccurrence).toHaveBeenCalledWith("o1"));
    await waitFor(() => expect(screen.queryByText("Aluguel")).toBeNull());
    expect(onChanged).toHaveBeenCalled();
  });

  it("ajusta valor e data antes de confirmar", async () => {
    const client = clientWith([item("o3", "Energia", "200.00", "2026-12-05", "2026-12")]);
    render(<PendingOccurrences client={client} onChanged={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Ajustar Energia" }));
    fireEvent.change(screen.getByLabelText("Valor (BRL)"), { target: { value: "23745" } });
    fireEvent.change(screen.getByLabelText("Data"), { target: { value: "2026-12-06" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar com ajuste" }));

    await waitFor(() => expect(client.confirmOccurrence).toHaveBeenCalledWith("o3", {
      amount: { amount: "237.45", currency: "BRL" }, occurredAt: "2026-12-06",
    }));
  });

  it("pede confirmação antes de descartar", async () => {
    const client = clientWith([rent]);
    render(<PendingOccurrences client={client} onChanged={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Descartar Aluguel" }));
    expect(client.discardOccurrence).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Descartar" }));

    await waitFor(() => expect(client.discardOccurrence).toHaveBeenCalledWith("o1"));
  });

  it("em 409 informa o erro, mantém acessível e recarrega a lista", async () => {
    const client = clientWith([rent]);
    vi.mocked(client.confirmOccurrence).mockRejectedValueOnce(new ApiError(409, "RECURRING_OCCURRENCE_RESOLVED", "A pendência já foi confirmada ou descartada.", "t"));
    render(<PendingOccurrences client={client} onChanged={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Confirmar Aluguel" }));

    expect(await screen.findByRole("alert")).toBeDefined();
    expect(screen.getByText(/já foi confirmada ou descartada/)).toBeDefined();
    await waitFor(() => expect(client.listPendingOccurrences).toHaveBeenCalledTimes(2));
  });
});
