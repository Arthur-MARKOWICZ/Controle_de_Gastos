import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PasswordRecoveryRequest, PasswordReset } from "./PasswordRecovery";

afterEach(() => {
  cleanup();
  window.history.replaceState(null, "", "/");
});

describe("recuperação de senha", () => {
  it("mantém a resposta genérica ao solicitar o link", async () => {
    const request = vi.fn().mockResolvedValue(undefined);
    render(<PasswordRecoveryRequest onRequest={request} />);

    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "pessoa@example.com" } });
    fireEvent.submit(screen.getByRole("button", { name: "Enviar link de recuperação" }).closest("form")!);

    expect((await screen.findByRole("status")).textContent).toMatch(/se houver uma conta/i);
    expect(request).toHaveBeenCalledWith("pessoa@example.com");
  });

  it("lê o token do fragmento, remove-o da URL e altera a senha", async () => {
    window.history.replaceState(null, "", "/redefinir-senha#token=token-seguro");
    const reset = vi.fn().mockResolvedValue(undefined);
    render(<PasswordReset onReset={reset} />);

    const newPassword = await screen.findByLabelText("Nova senha");
    expect(window.location.hash).toBe("");
    fireEvent.change(newPassword, { target: { value: "uma senha nova segura" } });
    fireEvent.change(screen.getByLabelText("Confirme a nova senha"), { target: { value: "uma senha nova segura" } });
    fireEvent.submit(screen.getByRole("button", { name: "Alterar senha" }).closest("form")!);

    expect((await screen.findByRole("status")).textContent).toMatch(/senha alterada/i);
    expect(reset).toHaveBeenCalledWith("token-seguro", "uma senha nova segura");
  });

  it("anuncia como erro o link sem token", async () => {
    window.history.replaceState(null, "", "/redefinir-senha");
    render(<PasswordReset onReset={vi.fn()} />);

    expect((await screen.findByRole("alert")).textContent).toMatch(/inválido ou expirou/i);
  });

  it("anuncia como erro o token recusado", async () => {
    window.history.replaceState(null, "", "/redefinir-senha#token=token-vencido");
    render(<PasswordReset onReset={vi.fn().mockRejectedValue(new Error("expirado"))} />);

    fireEvent.change(await screen.findByLabelText("Nova senha"), { target: { value: "uma senha nova segura" } });
    fireEvent.change(screen.getByLabelText("Confirme a nova senha"), { target: { value: "uma senha nova segura" } });
    fireEvent.submit(screen.getByRole("button", { name: "Alterar senha" }).closest("form")!);

    expect((await screen.findByRole("alert")).textContent).toMatch(/inválido ou expirou/i);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("anuncia como erro senhas diferentes", async () => {
    window.history.replaceState(null, "", "/redefinir-senha#token=token-seguro");
    render(<PasswordReset onReset={vi.fn()} />);

    fireEvent.change(await screen.findByLabelText("Nova senha"), { target: { value: "uma senha nova segura" } });
    fireEvent.change(screen.getByLabelText("Confirme a nova senha"), { target: { value: "outra senha qualquer" } });
    fireEvent.submit(screen.getByRole("button", { name: "Alterar senha" }).closest("form")!);

    expect((await screen.findByRole("alert")).textContent).toBe("As senhas precisam ser iguais.");
  });
});
