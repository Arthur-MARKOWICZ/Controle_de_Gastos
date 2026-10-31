import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthClient, LoginMethods, MfaStatus } from "../../auth/auth-client";
import { SecuritySettings } from "./SecuritySettings";

afterEach(cleanup);

function fakeClient(
  loginMethods: LoginMethods,
  overrides: Partial<AuthClient> = {},
): AuthClient {
  return {
    mfaStatus: vi.fn().mockResolvedValue({ status: "DISABLED", pendingExpiresAt: null } satisfies MfaStatus),
    loginMethods: vi.fn().mockResolvedValue(loginMethods),
    connectOAuth: vi.fn().mockResolvedValue(undefined),
    unlinkProvider: vi.fn().mockResolvedValue(undefined),
    addPassword: vi.fn().mockResolvedValue(undefined),
    disableMfa: vi.fn().mockResolvedValue(undefined),
    regenerateRecoveryCodes: vi.fn().mockResolvedValue(["AAAA-BBBB"]),
    ...overrides,
  } as unknown as AuthClient;
}

describe("SecuritySettings - contas conectadas", () => {
  it("mostra os dois provedores e permite conectar um que ainda não está vinculado", async () => {
    const client = fakeClient({ hasPassword: true, linkedProviders: [] });
    render(<SecuritySettings client={client} email="ana@example.com" onLoggedOut={vi.fn()} onLogout={vi.fn()} />);

    const connectButton = await screen.findByRole("button", { name: "Conectar Google" });
    fireEvent.click(connectButton);

    expect(client.connectOAuth).toHaveBeenCalledWith("google");
  });

  it("desabilita desconectar quando é o único método de login restante", async () => {
    const client = fakeClient({ hasPassword: false, linkedProviders: ["google"] });
    render(<SecuritySettings client={client} email="ana@example.com" onLoggedOut={vi.fn()} onLogout={vi.fn()} />);

    const disconnectButton = await screen.findByRole("button", { name: "Desconectar Google" });

    expect(disconnectButton).toHaveProperty("disabled", true);
  });

  it("permite desconectar quando ainda sobra outro método de login", async () => {
    const client = fakeClient({ hasPassword: false, linkedProviders: ["google", "github"] });
    render(<SecuritySettings client={client} email="ana@example.com" onLoggedOut={vi.fn()} onLogout={vi.fn()} />);

    const disconnectButton = await screen.findByRole("button", { name: "Desconectar Google" });
    expect(disconnectButton).toHaveProperty("disabled", false);
    fireEvent.click(disconnectButton);

    expect(client.unlinkProvider).toHaveBeenCalledWith("google");
    expect(await screen.findByRole("button", { name: "Conectar Google" })).toBeDefined();
  });

  it("oferece cadastrar senha só quando a conta ainda não tem uma, e salva com sucesso", async () => {
    const client = fakeClient({ hasPassword: false, linkedProviders: ["google"] });
    render(<SecuritySettings client={client} email="ana@example.com" onLoggedOut={vi.fn()} onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Cadastrar senha" }));
    fireEvent.change(screen.getByLabelText("Nova senha"), { target: { value: "uma frase bem segura" } });
    fireEvent.submit(screen.getByRole("button", { name: "Salvar senha" }).closest("form")!);

    expect(client.addPassword).toHaveBeenCalledWith("uma frase bem segura");
    const disconnectButton = await screen.findByRole("button", { name: "Desconectar Google" });
    expect(disconnectButton).toHaveProperty("disabled", false);
  });

  it("mostra o aviso de conexão vindo do retorno do provedor", async () => {
    const client = fakeClient({ hasPassword: true, linkedProviders: ["google"] });
    render(<SecuritySettings client={client} email="ana@example.com" connectionNotice="Conta Google conectada com sucesso." onLoggedOut={vi.fn()} onLogout={vi.fn()} />);

    expect(await screen.findByText("Conta Google conectada com sucesso.")).toBeDefined();
  });
});

describe("SecuritySettings - MFA", () => {
  it("não trata falha de consulta como Desativada e oferece retry", async () => {
    const client = fakeClient(
      { hasPassword: true, linkedProviders: [] },
      { mfaStatus: vi.fn().mockRejectedValue(new Error("rede")) },
    );
    render(<SecuritySettings client={client} email="ana@example.com" onLoggedOut={vi.fn()} onLogout={vi.fn()} />);

    expect(await screen.findByRole("alert")).toBeDefined();
    expect(screen.queryByText(/desativada/i)).toBeNull();
    expect(screen.getByRole("button", { name: "Tentar novamente" })).toBeDefined();
  });

  it("ao desativar MFA notifica mfa-disabled", async () => {
    const onLoggedOut = vi.fn();
    const client = fakeClient(
      { hasPassword: true, linkedProviders: [] },
      { mfaStatus: vi.fn().mockResolvedValue({ status: "ENABLED", pendingExpiresAt: null } satisfies MfaStatus) },
    );
    render(<SecuritySettings client={client} email="ana@example.com" onLoggedOut={onLoggedOut} onLogout={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Desativar MFA" }));
    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "senha-correta-12" } });
    fireEvent.submit(screen.getByRole("button", { name: "Confirmar desativação" }).closest("form")!);

    await waitFor(() => expect(onLoggedOut).toHaveBeenCalledWith("mfa-disabled"));
    expect(client.disableMfa).toHaveBeenCalledWith("senha-correta-12");
  });
});
