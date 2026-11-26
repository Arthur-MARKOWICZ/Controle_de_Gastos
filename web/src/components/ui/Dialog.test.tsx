import { useState } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { Dialog } from "./Dialog";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

afterEach(cleanup);

describe("Dialog", () => {
  it("foca o primeiro campo do corpo e não o botão Fechar", async () => {
    render(
      <Dialog open onClose={vi.fn()} title="Registrar gasto">
        <label htmlFor="valor">Valor</label>
        <input id="valor" />
        <button type="button">Salvar</button>
      </Dialog>,
    );

    await waitFor(() => {
      expect(document.activeElement).toBe(screen.getByLabelText("Valor"));
    });
  });

  it("restaura o foco ao gatilho ao fechar", async () => {
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>Abrir</button>
          <Dialog open={open} onClose={() => setOpen(false)} title="Editar">
            <input aria-label="Campo" />
          </Dialog>
        </>
      );
    }

    render(<Harness />);

    const trigger = screen.getByRole("button", { name: "Abrir" });
    trigger.focus();
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByLabelText("Campo")).toBeDefined();
    });

    fireEvent.click(screen.getByRole("button", { name: "Fechar" }));

    await waitFor(() => {
      expect(document.activeElement).toBe(trigger);
    });
  });

  it("prende o Tab dentro do diálogo", async () => {
    render(
      <Dialog open onClose={vi.fn()} title="Formulário">
        <input aria-label="Primeiro" />
        <button type="button">Último</button>
      </Dialog>,
    );

    const first = screen.getByLabelText("Primeiro");
    const last = screen.getByRole("button", { name: "Último" });
    const close = screen.getByRole("button", { name: "Fechar" });

    await waitFor(() => expect(document.activeElement).toBe(first));

    last.focus();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Tab" });
    expect(document.activeElement).toBe(close);

    close.focus();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(last);
  });

  it("fechado não renderiza o conteúdo", () => {
    render(<Dialog open={false} onClose={vi.fn()} title="Oculto"><p>Conteúdo secreto</p></Dialog>);

    expect(screen.queryByText("Conteúdo secreto")).toBeNull();
    expect(screen.queryByRole("heading", { name: "Oculto", hidden: true })).toBeNull();
  });

  it("clique no fundo fecha, clique no conteúdo não fecha", () => {
    const onClose = vi.fn();
    render(<Dialog open onClose={onClose} title="Editar"><p>Corpo</p></Dialog>);

    fireEvent.click(screen.getByText("Corpo"));
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("dialog"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("Esc chama onClose e impede o fechamento nativo", () => {
    const onClose = vi.fn();
    render(<Dialog open onClose={onClose} title="Editar"><p>Corpo</p></Dialog>);

    const event = new Event("cancel", { cancelable: true });
    screen.getByRole("dialog").dispatchEvent(event);

    expect(onClose).toHaveBeenCalledOnce();
    expect(event.defaultPrevented).toBe(true);
  });

  it("teclas que não são Tab não mexem no foco", async () => {
    render(<Dialog open onClose={vi.fn()} title="Formulário"><input aria-label="Primeiro" /></Dialog>);
    const first = screen.getByLabelText("Primeiro");
    await waitFor(() => expect(document.activeElement).toBe(first));

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Enter" });

    expect(document.activeElement).toBe(first);
  });
});
