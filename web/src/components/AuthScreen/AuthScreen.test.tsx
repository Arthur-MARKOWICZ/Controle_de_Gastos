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

  it("oferece link para a política de privacidade sem exigir login", () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} />);

    expect(screen.getByRole("link", { name: "Política de privacidade" })).toHaveProperty(
      "href",
      "http://localhost:3000/privacidade",
    );
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

  it("anuncia a falha de login como erro, não como aviso", () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} externalError="Não foi possível entrar com os dados informados." />);

    expect(screen.getByRole("alert").textContent).toBe("Não foi possível entrar com os dados informados.");
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("mostra aviso informativo como status", () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} notice="MFA ativado com sucesso." />);

    expect(screen.getByRole("status").textContent).toBe("MFA ativado com sucesso.");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("anuncia a falha de cadastro como erro", async () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn().mockRejectedValue(new Error("falha"))} />);

    fireEvent.click(screen.getByRole("button", { name: "Criar conta" }));
    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "pessoa@example.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "frase segura de teste" } });
    fireEvent.submit(screen.getByRole("button", { name: "Concluir cadastro" }).closest("form")!);

    expect((await screen.findByRole("alert")).textContent).toBe("Revise os dados e tente novamente.");
  });

  it("não mostra login social quando nenhum manipulador é informado", () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Continuar com Google" })).toBeNull();
  });

  it("aciona o login social com o provedor escolhido", () => {
    const onOAuthLogin = vi.fn();
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} onOAuthLogin={onOAuthLogin} />);

    fireEvent.click(screen.getByRole("button", { name: "Continuar com Google" }));
    fireEvent.click(screen.getByRole("button", { name: "Continuar com GitHub" }));

    expect(onOAuthLogin).toHaveBeenNthCalledWith(1, "google");
    expect(onOAuthLogin).toHaveBeenNthCalledWith(2, "github");
  });

  it("orienta quem está entrando pela primeira vez a ir para a tela de cadastro", () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} onOAuthLogin={vi.fn()} />);

    expect(screen.getByText(/Primeira vez por aqui/)).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "Crie sua conta" }));

    expect(screen.getByRole("heading", { name: "Crie sua conta" })).toBeDefined();
    expect(screen.queryByText(/Primeira vez por aqui/)).toBeNull();
  });

  it("senha errada no login não mostra sucesso, não troca de tela e libera nova tentativa", async () => {
    const onLogin = vi.fn().mockRejectedValue(new Error("401"));
    render(<AuthScreen onLogin={onLogin} onRegister={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "conta-x@example.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "senha totalmente errada" } });
    const form = screen.getByLabelText("E-mail").closest("form")!;
    fireEvent.submit(form);

    await vi.waitFor(() => expect(onLogin).toHaveBeenCalledWith("conta-x@example.com", "senha totalmente errada"));
    await vi.waitFor(() => expect(form.querySelector("button[type=submit]")).toHaveProperty("disabled", false));
    expect(screen.getByRole("heading", { name: "Entre na sua conta" })).toBeDefined();
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("cadastro recusado permanece na tela de cadastro e não anuncia cadastro recebido", async () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn().mockRejectedValue(new Error("400"))} />);

    fireEvent.click(screen.getByRole("button", { name: "Criar conta" }));
    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "pessoa@example.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "frase segura de teste" } });
    fireEvent.submit(screen.getByRole("button", { name: "Concluir cadastro" }).closest("form")!);

    await screen.findByRole("alert");
    expect(screen.getByRole("button", { name: "Concluir cadastro" })).toBeDefined();
    expect(screen.queryByText(/Agora você pode entrar/)).toBeNull();
  });

  it("trocar entre entrar e criar conta preserva o e-mail e apaga senha e mensagens anteriores", async () => {
    render(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn().mockRejectedValue(new Error("400"))} />);
    fireEvent.click(screen.getByRole("button", { name: "Criar conta" }));
    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "pessoa@example.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "frase segura de teste" } });
    fireEvent.submit(screen.getByRole("button", { name: "Concluir cadastro" }).closest("form")!);
    await screen.findByRole("alert");

    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect((screen.getByLabelText("E-mail") as HTMLInputElement).value).toBe("pessoa@example.com");
    expect((screen.getByLabelText("Senha") as HTMLInputElement).value).toBe("");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("enquanto entra, o botão fica desabilitado e não envia duas vezes", async () => {
    const onLogin = vi.fn(() => new Promise<void>(() => undefined));
    render(<AuthScreen onLogin={onLogin} onRegister={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "pessoa@example.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "frase segura de teste" } });
    const form = screen.getByLabelText("E-mail").closest("form")!;
    fireEvent.submit(form);

    await vi.waitFor(() => expect(form.querySelector("button[type=submit]")).toHaveProperty("disabled", true));
    expect(onLogin).toHaveBeenCalledOnce();
  });
});
