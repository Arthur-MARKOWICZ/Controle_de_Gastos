import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
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

  it("por padrão é type=button e não envia o formulário em volta", () => {
    const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault());
    render(<form onSubmit={onSubmit}><Button>Atualizar</Button></form>);

    fireEvent.click(screen.getByRole("button", { name: "Atualizar" }));

    expect(screen.getByRole("button", { name: "Atualizar" }).getAttribute("type")).toBe("button");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("botão ocupado não dispara o clique", () => {
    const onClick = vi.fn();
    render(<Button busy onClick={onClick}>Salvando</Button>);

    fireEvent.click(screen.getByRole("button", { name: "Salvando" }));

    expect(onClick).not.toHaveBeenCalled();
  });

  it("botão desabilitado não dispara o clique nem fica marcado como ocupado", () => {
    const onClick = vi.fn();
    render(<Button disabled onClick={onClick}>Enviar</Button>);

    const button = screen.getByRole("button", { name: "Enviar" });
    fireEvent.click(button);

    expect(onClick).not.toHaveBeenCalled();
    expect(button.getAttribute("aria-busy")).toBeNull();
  });

  it("type=submit envia o formulário e preserva a classe extra", () => {
    const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault());
    render(<form onSubmit={onSubmit}><Button type="submit" className="extra">Salvar</Button></form>);

    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(onSubmit).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: "Salvar" }).className).toMatch(/extra/);
  });
});
