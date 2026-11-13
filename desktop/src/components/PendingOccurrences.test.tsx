import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PendingOccurrences } from "./PendingOccurrences";
import { NO_PENDING, PENDING_PATH, pendingOccurrence } from "../lib/fixtures";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";

afterEach(() => {
  cleanup();
  resetIpc();
});

const aluguel = pendingOccurrence();
const internet = pendingOccurrence({
  id: "oc-2",
  description: "Internet",
  suggestedAmount: { amount: "99.90", currency: "BRL" },
  dueDate: "2026-10-10",
  competence: "2026-10",
});

describe("pendências de recorrência no desktop", () => {
  it("lista pendências do mês e vencidas sob rótulo em texto", async () => {
    installFakeIpc().onApi(PENDING_PATH, { status: 200, body: { current: [aluguel], overdue: [internet] } });
    render(<PendingOccurrences onChanged={vi.fn()} onAuthRequired={vi.fn()} />);

    const mes = await screen.findByRole("region", { name: "Pendências do mês" });
    expect(within(mes).getByText("Aluguel")).toBeTruthy();
    expect(within(mes).getByText(/1\.800,00/)).toBeTruthy();
    const vencidas = screen.getByRole("region", { name: "Vencidas" });
    expect(within(vencidas).getByText("Internet")).toBeTruthy();
  });

  it("sem pendências não mostra o bloco", async () => {
    const ipc = installFakeIpc().onApi(PENDING_PATH, { status: 200, body: NO_PENDING });
    const { container } = render(<PendingOccurrences onChanged={vi.fn()} onAuthRequired={vi.fn()} />);

    await waitFor(() => expect(ipc.countPath(PENDING_PATH)).toBe(1));
    expect(container.innerHTML).toBe("");
  });

  it("confirma pela API e recarrega os saldos", async () => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    const ipc = installFakeIpc()
      .onApi(PENDING_PATH, { status: 200, body: { current: [aluguel], overdue: [] } })
      .onApi("/api/v1/recurring-occurrences/oc-1/confirm", {
        status: 200,
        body: { id: "oc-1", status: "CONFIRMED", ledgerEntryId: "l-1" },
      });
    render(<PendingOccurrences onChanged={onChanged} onAuthRequired={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "Confirmar Aluguel" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    const call = ipc.apiCalls().find((c) => c.args.path === "/api/v1/recurring-occurrences/oc-1/confirm");
    expect(call?.args.method).toBe("POST");
    expect(screen.queryByText("Aluguel")).toBeNull();
  });

  it("ajusta valor e data antes de confirmar", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PENDING_PATH, { status: 200, body: { current: [aluguel], overdue: [] } })
      .onApi("/api/v1/recurring-occurrences/oc-1/confirm", { status: 200, body: { id: "oc-1" } });
    render(<PendingOccurrences onChanged={vi.fn()} onAuthRequired={vi.fn()} today="2026-11-13" />);

    await user.click(await screen.findByRole("button", { name: "Ajustar Aluguel" }));
    const valor = screen.getByLabelText("Valor");
    await user.clear(valor);
    await user.type(valor, "185000");
    await user.click(screen.getByRole("button", { name: "Confirmar com ajuste" }));

    await waitFor(() => expect(ipc.countPath("/api/v1/recurring-occurrences/oc-1/confirm")).toBe(1));
    const call = ipc.apiCalls().find((c) => c.args.path === "/api/v1/recurring-occurrences/oc-1/confirm");
    expect(JSON.parse(String(call?.args.body))).toEqual({
      amount: { amount: "1850.00", currency: "BRL" },
      occurredAt: "2026-11-05",
    });
  });

  it("descarta só depois de confirmar a intenção", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PENDING_PATH, { status: 200, body: { current: [aluguel], overdue: [] } })
      .onApi("/api/v1/recurring-occurrences/oc-1/discard", { status: 204, body: "" });
    render(<PendingOccurrences onChanged={vi.fn()} onAuthRequired={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "Descartar Aluguel" }));
    expect(ipc.countPath("/api/v1/recurring-occurrences/oc-1/discard")).toBe(0);
    await user.click(screen.getByRole("button", { name: "Sim, descartar Aluguel" }));

    await waitFor(() => expect(ipc.countPath("/api/v1/recurring-occurrences/oc-1/discard")).toBe(1));
  });

  it("recusa da API mantém a pendência e mostra o motivo", async () => {
    const user = userEvent.setup();
    installFakeIpc()
      .onApi(PENDING_PATH, { status: 200, body: { current: [aluguel], overdue: [] } })
      .onApi("/api/v1/recurring-occurrences/oc-1/confirm", {
        status: 409,
        body: { detail: "A pendência já foi confirmada ou descartada. Recarregue a lista." },
      });
    render(<PendingOccurrences onChanged={vi.fn()} onAuthRequired={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "Confirmar Aluguel" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("já foi confirmada ou descartada");
    expect(screen.getByText("Aluguel")).toBeTruthy();
  });

  it("ações alcançáveis só pelo teclado", async () => {
    const user = userEvent.setup();
    installFakeIpc().onApi(PENDING_PATH, { status: 200, body: { current: [aluguel], overdue: [] } });
    render(<PendingOccurrences onChanged={vi.fn()} onAuthRequired={vi.fn()} />);
    await screen.findByText("Aluguel");

    await user.tab();
    expect(screen.getByRole("button", { name: "Confirmar Aluguel" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "Ajustar Aluguel" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "Descartar Aluguel" })).toHaveFocus();
  });
});
