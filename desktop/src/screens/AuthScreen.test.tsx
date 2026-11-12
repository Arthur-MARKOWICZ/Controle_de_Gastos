import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthScreen } from "./AuthScreen";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";

afterEach(() => {
  cleanup();
  resetIpc();
});

async function preencherCredenciais(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("E-mail"), "titular@exemplo.com");
  await user.type(screen.getByLabelText("Senha"), "senha-correta");
  await user.click(screen.getByRole("button", { name: "Entrar" }));
}

describe("5.1 login com e-mail e senha", () => {
  it("sucesso sem MFA entra na área autenticada", async () => {
    const user = userEvent.setup();
    installFakeIpc().on("login", () => ({
      status: "authenticated",
      session: { email: "titular@exemplo.com" },
    }));
    const onAuthenticated = vi.fn();

    render(<AuthScreen onAuthenticated={onAuthenticated} />);
    await preencherCredenciais(user);

    await waitFor(() =>
      expect(onAuthenticated).toHaveBeenCalledWith({ email: "titular@exemplo.com" }),
    );
  });

  it("credencial inválida mostra mensagem genérica que não revela a conta", async () => {
    const user = userEvent.setup();
    installFakeIpc().on("login", () => {
      throw { message: "E-mail ou senha incorretos.", authRequired: false };
    });
    const onAuthenticated = vi.fn();

    render(<AuthScreen onAuthenticated={onAuthenticated} />);
    await preencherCredenciais(user);

    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("E-mail ou senha incorretos.");
    expect(alerta.textContent?.toLowerCase()).not.toContain("não existe");
    expect(alerta.textContent?.toLowerCase()).not.toContain("não cadastrado");
    expect(onAuthenticated).not.toHaveBeenCalled();
    // Continua na tela de credenciais.
    expect(screen.getByLabelText("Senha")).toBeTruthy();
  });

  it("não chama a API sem e-mail e senha", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc();

    render(<AuthScreen onAuthenticated={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Informe e-mail e senha.");
    expect(ipc.calls).toHaveLength(0);
  });
});

describe("5.2 desafio TOTP", () => {
  async function chegarAoDesafio(user: ReturnType<typeof userEvent.setup>, aoVerificar: () => unknown) {
    installFakeIpc()
      .on("login", () => ({ status: "mfaRequired", challengeId: "desafio-1", expiresIn: 300 }))
      .on("verify_mfa", aoVerificar);
    const onAuthenticated = vi.fn();
    render(<AuthScreen onAuthenticated={onAuthenticated} />);
    await preencherCredenciais(user);
    await screen.findByLabelText("Código do autenticador");
    return onAuthenticated;
  }

  it("código válido conclui a sessão", async () => {
    const user = userEvent.setup();
    const onAuthenticated = await chegarAoDesafio(user, () => ({
      status: "authenticated",
      session: { email: "titular@exemplo.com" },
    }));

    await user.type(screen.getByLabelText("Código do autenticador"), "123456");
    await user.click(screen.getByRole("button", { name: "Verificar" }));

    await waitFor(() =>
      expect(onAuthenticated).toHaveBeenCalledWith({ email: "titular@exemplo.com" }),
    );
  });

  it("código inválido permanece no desafio e não abre a sessão", async () => {
    const user = userEvent.setup();
    const onAuthenticated = await chegarAoDesafio(user, () => {
      throw { message: "Código de verificação inválido ou expirado.", authRequired: false };
    });

    await user.type(screen.getByLabelText("Código do autenticador"), "000000");
    await user.click(screen.getByRole("button", { name: "Verificar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Código de verificação inválido ou expirado.",
    );
    expect(onAuthenticated).not.toHaveBeenCalled();
    // Segue no desafio, não volta para a senha.
    expect(screen.getByLabelText("Código do autenticador")).toBeTruthy();
    expect(screen.queryByLabelText("Senha")).toBeNull();
  });

  it("o desafio recebe o challengeId que o login devolveu", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .on("login", () => ({ status: "mfaRequired", challengeId: "desafio-7", expiresIn: 300 }))
      .on("verify_mfa", () => ({
        status: "authenticated",
        session: { email: "titular@exemplo.com" },
      }));

    render(<AuthScreen onAuthenticated={vi.fn()} />);
    await preencherCredenciais(user);
    await user.type(await screen.findByLabelText("Código do autenticador"), "123456");
    await user.click(screen.getByRole("button", { name: "Verificar" }));

    await waitFor(() => {
      const chamada = ipc.calls.find((c) => c.command === "verify_mfa");
      expect(chamada?.args).toEqual({ challengeId: "desafio-7", code: "123456" });
    });
  });
});
