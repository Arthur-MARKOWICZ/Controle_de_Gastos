import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthClient } from "../../auth/auth-client";
import { MfaSettings } from "./MfaSettings";

afterEach(cleanup);

function fakeClient(overrides: Partial<AuthClient> = {}): AuthClient {
  return {
    startMfaEnrollment: vi.fn().mockResolvedValue({
      otpauthUri: "otpauth://totp/example",
      qrImageDataUri: "data:image/png;base64,aGVsbG8=",
      manualEntryKey: "JBSWY3DPEHPK3PXP",
      pendingExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
    }),
    confirmMfaEnrollment: vi.fn().mockResolvedValue(
      Array.from({ length: 10 }, (_, index) => `CODE${index}-XXXXX`),
    ),
    ...overrides,
  } as unknown as AuthClient;
}

describe("MfaSettings", () => {
  it("pede a senha, mostra o QR Code e a chave manual após iniciar a configuração", async () => {
    const client = fakeClient();
    render(<MfaSettings client={client} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));

    expect(await screen.findByAltText("QR Code para configurar o aplicativo autenticador"))
      .toHaveProperty("src", "data:image/png;base64,aGVsbG8=");
    expect(screen.getByText("JBSWY3DPEHPK3PXP")).toBeDefined();
    expect(client.startMfaEnrollment).toHaveBeenCalledWith("frase segura de teste", undefined);
  });

  it("usa o token restrito quando fornecido, para o fluxo de recuperação", async () => {
    const client = fakeClient();
    render(<MfaSettings client={client} restrictedToken="token-restrito" onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));

    await screen.findByAltText("QR Code para configurar o aplicativo autenticador");
    expect(client.startMfaEnrollment).toHaveBeenCalledWith("frase segura de teste", "token-restrito");
  });

  it("exibe os dez recovery codes uma única vez após confirmar o código", async () => {
    const client = fakeClient();
    render(<MfaSettings client={client} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    fireEvent.change(await screen.findByLabelText("Código de 6 dígitos"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Ativar MFA" }));

    expect(await screen.findByText("CODE0-XXXXX")).toBeDefined();
    expect(screen.getAllByRole("listitem")).toHaveLength(10);
    expect(client.confirmMfaEnrollment).toHaveBeenCalledWith("123456", undefined);
  });

  it("nunca grava dados de MFA em localStorage ou sessionStorage", async () => {
    const localSpy = vi.spyOn(Storage.prototype, "setItem");
    const client = fakeClient();
    render(<MfaSettings client={client} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    fireEvent.change(await screen.findByLabelText("Código de 6 dígitos"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Ativar MFA" }));
    await screen.findByText("CODE0-XXXXX");

    expect(localSpy).not.toHaveBeenCalled();
    localSpy.mockRestore();
  });
});
