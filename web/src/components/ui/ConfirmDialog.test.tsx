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

  it("Cancelar chama só onCancel", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open title="Excluir?" message="Sem volta." confirmLabel="Excluir" onConfirm={onConfirm} onCancel={onCancel} />);

    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("ocupado bloqueia confirmar de novo e cancelar no meio da operação", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open busy title="Excluir?" message="Sem volta." confirmLabel="Excluir" onConfirm={onConfirm} onCancel={onCancel} />);

    fireEvent.click(screen.getByRole("button", { name: "Excluir" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(onConfirm).not.toHaveBeenCalled();
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("fechado não mostra a mensagem nem a ação destrutiva", () => {
    render(<ConfirmDialog open={false} title="Excluir?" message="Sem volta." confirmLabel="Excluir" onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.queryByText("Sem volta.")).toBeNull();
    expect(screen.queryByRole("button", { name: "Excluir", hidden: true })).toBeNull();
  });

  it("Esc cancela sem confirmar", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open title="Excluir?" message="Sem volta." confirmLabel="Excluir" onConfirm={onConfirm} onCancel={onCancel} />);

    screen.getByRole("dialog").dispatchEvent(new Event("cancel", { cancelable: true }));

    expect(onCancel).toHaveBeenCalledOnce();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
