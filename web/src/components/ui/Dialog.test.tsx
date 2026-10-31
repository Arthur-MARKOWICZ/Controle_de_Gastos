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
});
