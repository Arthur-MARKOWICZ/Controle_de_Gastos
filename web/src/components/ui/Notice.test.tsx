import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Notice } from "./Notice";

afterEach(cleanup);

describe("Notice", () => {
  it("usa alert para danger e warning, status para os demais", () => {
    const { rerender } = render(<Notice tone="danger">Falha</Notice>);
    expect(screen.getByRole("alert").textContent).toMatch(/Falha/);

    rerender(<Notice tone="warning">Atenção</Notice>);
    expect(screen.getByRole("alert").textContent).toMatch(/Atenção/);

    rerender(<Notice tone="success">Ok</Notice>);
    expect(screen.getByRole("status").textContent).toMatch(/Ok/);

    rerender(<Notice tone="info">Info</Notice>);
    expect(screen.getByRole("status").textContent).toMatch(/Info/);
  });

  it("oferece botão de fechar opcional", () => {
    const onClose = vi.fn();
    render(<Notice tone="success" onClose={onClose}>Salvo</Notice>);

    fireEvent.click(screen.getByRole("button", { name: "Fechar aviso" }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
