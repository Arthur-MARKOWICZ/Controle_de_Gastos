import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider, useToast } from "./ToastProvider";

afterEach(cleanup);

function Trigger({ onUndo }: { onUndo?: () => void }) {
  const { showToast } = useToast();
  return (
    <button
      type="button"
      onClick={() =>
        showToast({
          message: "Gasto excluído.",
          action: onUndo ? { label: "Desfazer", onClick: onUndo } : undefined,
        })
      }
    >
      Disparar
    </button>
  );
}

describe("ToastProvider", () => {
  it("exibe notificação acessível e executa a ação Desfazer", () => {
    const onUndo = vi.fn();
    render(
      <ToastProvider>
        <Trigger onUndo={onUndo} />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Disparar" }));

    expect(screen.getByRole("status").textContent).toContain("Gasto excluído.");
    fireEvent.click(screen.getByRole("button", { name: "Desfazer" }));
    expect(onUndo).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("fecha ao clicar no botão de fechar", () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Disparar" }));
    fireEvent.click(screen.getByRole("button", { name: "Fechar notificação" }));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("aceita mensagem string, tom danger e pausa o timer no hover", () => {
    function StringAndDangerTrigger() {
      const { showToast } = useToast();
      return (
        <>
          <button type="button" onClick={() => showToast("Salvo.")}>
            String
          </button>
          <button
            type="button"
            onClick={() =>
              showToast({
                message: "Falha ao salvar.",
                tone: "danger",
                durationMs: 0,
              })
            }
          >
            Danger
          </button>
        </>
      );
    }

    render(
      <ToastProvider>
        <StringAndDangerTrigger />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "String" }));
    const status = screen.getByRole("status");
    expect(status.textContent).toContain("Salvo.");
    fireEvent.mouseEnter(status);
    fireEvent.mouseLeave(status);

    fireEvent.click(screen.getByRole("button", { name: "Danger" }));
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("Falha ao salvar.");
    expect(alert.getAttribute("aria-live")).toBe("assertive");
  });
});
