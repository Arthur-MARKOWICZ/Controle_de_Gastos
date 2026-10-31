import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Button } from "./Button";

afterEach(cleanup);

describe("Button", () => {
  it("expõe variantes e altura mínima de 44px", () => {
    render(
      <>
        <Button variant="primary">Salvar</Button>
        <Button variant="secondary">Cancelar</Button>
        <Button variant="text">Atualizar</Button>
        <Button variant="danger">Arquivar</Button>
      </>,
    );

    expect(screen.getByRole("button", { name: "Salvar" }).className).toMatch(/primary/);
    expect(screen.getByRole("button", { name: "Cancelar" }).className).toMatch(/secondary/);
    expect(screen.getByRole("button", { name: "Atualizar" }).className).toMatch(/text/);
    expect(screen.getByRole("button", { name: "Arquivar" }).className).toMatch(/danger/);
  });

  it("marca ocupado com aria-busy e disabled", () => {
    render(<Button busy>Salvando</Button>);

    const button = screen.getByRole("button", { name: "Salvando" });
    expect(button.getAttribute("aria-busy")).toBe("true");
    expect(button).toHaveProperty("disabled", true);
  });
});
