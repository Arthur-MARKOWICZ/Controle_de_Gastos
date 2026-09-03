import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthScreen } from "./AuthScreen";

afterEach(cleanup);

describe("AuthScreen", () => {
  it("oferece recuperação de senha na tela de login", () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Entre na sua conta" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Esqueci minha senha" })).toHaveProperty("href", "http://localhost:3000/recuperar-senha");
    expect(screen.getByLabelText("E-mail")).toBeDefined();
    expect(screen.getByLabelText("Senha")).toBeDefined();
  });

  it("mantém o cadastro separado e comunica que não há login automático", async () => {
    const register = vi.fn().mockResolvedValue(undefined);
    render(<AuthScreen onLogin={vi.fn()} onRegister={register} />);

    fireEvent.click(screen.getByRole("button", { name: "Criar conta" }));
    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "Pessoa@Example.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "frase segura de teste" } });
    fireEvent.submit(screen.getByRole("button", { name: "Concluir cadastro" }).closest("form")!);

    expect((await screen.findByRole("status")).textContent).toMatch(/agora você pode entrar/i);
    expect(register).toHaveBeenCalledWith("Pessoa@Example.com", "frase segura de teste");
  });
});
