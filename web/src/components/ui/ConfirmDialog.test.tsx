import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

afterEach(cleanup);

describe("ConfirmDialog", () => {
  it("mostra título, consequência e ação nomeada em estilo danger", async () => {
    const onConfirm = vi.fn();
    render(
      <ConfirmDialog
        open
        title="Encerrar Combustível?"
        message="A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no histórico."
        confirmLabel="Encerrar"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.getByRole("heading", { name: "Encerrar Combustível?" })).toBeDefined();
    expect(screen.getByText(/deixa de aparecer nos próximos meses/i)).toBeDefined();
    const confirm = screen.getByRole("button", { name: "Encerrar" });
    expect(confirm.className).toMatch(/danger/);
    fireEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("foca Cancelar ao abrir", async () => {
    render(
      <ConfirmDialog
        open
        title="Excluir lançamento?"
        message="O valor volta para o saldo."
        confirmLabel="Excluir"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cancelar" }));
    });
  });
});
