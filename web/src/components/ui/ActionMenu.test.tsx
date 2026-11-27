import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ActionMenu } from "./ActionMenu";

afterEach(cleanup);

describe("ActionMenu", () => {
  it("abre o menu, executa item e fecha com Escape ou clique fora", () => {
    const onEdit = vi.fn();
    render(
      <div>
        <ActionMenu
          ariaLabel="Mais ações de Mercado"
          items={[
            { key: "edit", label: "Editar", onClick: onEdit },
            { key: "archive", label: "Encerrar", danger: true, onClick: vi.fn() },
          ]}
        />
        <button type="button">Fora</button>
      </div>,
    );

    const trigger = screen.getByRole("button", { name: "Mais ações de Mercado" });
    expect(trigger.getAttribute("aria-expanded")).toBe("false");

    fireEvent.click(trigger);
    expect(trigger.getAttribute("aria-expanded")).toBe("true");
    expect(screen.getByRole("menu")).toBeDefined();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("menu")).toBeNull();
    expect(document.activeElement).toBe(trigger);

    fireEvent.click(trigger);
    fireEvent.mouseDown(screen.getByRole("button", { name: "Fora" }));
    expect(screen.queryByRole("menu")).toBeNull();

    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole("menuitem", { name: "Editar" }));
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("navega itens com setas e usa rótulo padrão", () => {
    render(
      <ActionMenu
        ariaLabel="Ações extras"
        items={[
          { key: "a", label: "Primeiro", onClick: vi.fn() },
          { key: "b", label: "Segundo", ariaLabel: "Segundo item", onClick: vi.fn() },
        ]}
      />,
    );

    const trigger = screen.getByRole("button", { name: "Ações extras" });
    expect(trigger.textContent).toContain("Mais");
    fireEvent.click(trigger);

    const menu = screen.getByRole("menu");
    const first = screen.getByRole("menuitem", { name: "Primeiro" });
    const second = screen.getByRole("menuitem", { name: "Segundo item" });
    first.focus();

    fireEvent.keyDown(menu, { key: "ArrowDown" });
    expect(document.activeElement).toBe(second);

    fireEvent.keyDown(menu, { key: "ArrowUp" });
    expect(document.activeElement).toBe(first);
  });
});
