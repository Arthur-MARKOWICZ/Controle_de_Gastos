import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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
    ...overrides,
  } as unknown as AuthClient;
}

describe("SecuritySettings - contas conectadas", () => {
  it("mostra os dois provedores e permite conectar um que ainda não está vinculado", async () => {
    const client = fakeClient({ hasPassword: true, linkedProviders: [] });
    render(<SecuritySettings client={client} onLoggedOut={vi.fn()} />);

    const connectButton = await screen.findByRole("button", { name: "Conectar Google" });
    fireEvent.click(connectButton);

    expect(client.connectOAuth).toHaveBeenCalledWith("google");
  });

  it("desabilita desconectar quando é o único método de login restante", async () => {
    const client = fakeClient({ hasPassword: false, linkedProviders: ["google"] });
    render(<SecuritySettings client={client} onLoggedOut={vi.fn()} />);

    const disconnectButton = await screen.findByRole("button", { name: "Desconectar Google" });

    expect(disconnectButton).toHaveProperty("disabled", true);
  });

  it("permite desconectar quando ainda sobra outro método de login", async () => {
    const client = fakeClient({ hasPassword: false, linkedProviders: ["google", "github"] });
    render(<SecuritySettings client={client} onLoggedOut={vi.fn()} />);

    const disconnectButton = await screen.findByRole("button", { name: "Desconectar Google" });
    expect(disconnectButton).toHaveProperty("disabled", false);
    fireEvent.click(disconnectButton);

    expect(client.unlinkProvider).toHaveBeenCalledWith("google");
    expect(await screen.findByRole("button", { name: "Conectar Google" })).toBeDefined();
  });

  it("oferece cadastrar senha só quando a conta ainda não tem uma, e salva com sucesso", async () => {
    const client = fakeClient({ hasPassword: false, linkedProviders: ["google"] });
    render(<SecuritySettings client={client} onLoggedOut={vi.fn()} />);

    fireEvent.click(await screen.findByRole("button", { name: "Cadastrar senha" }));
    fireEvent.change(screen.getByLabelText("Nova senha"), { target: { value: "uma frase bem segura" } });
    fireEvent.submit(screen.getByRole("button", { name: "Salvar senha" }).closest("form")!);

    expect(client.addPassword).toHaveBeenCalledWith("uma frase bem segura");
    const disconnectButton = await screen.findByRole("button", { name: "Desconectar Google" });
    expect(disconnectButton).toHaveProperty("disabled", false);
  });

  it("mostra o aviso de conexão vindo do retorno do provedor", async () => {
    const client = fakeClient({ hasPassword: true, linkedProviders: ["google"] });
    render(<SecuritySettings client={client} connectionNotice="Conta Google conectada com sucesso." onLoggedOut={vi.fn()} />);

    expect(await screen.findByText("Conta Google conectada com sucesso.")).toBeDefined();
  });
});
