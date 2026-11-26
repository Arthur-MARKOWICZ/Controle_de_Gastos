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

  it("permite gerar um novo QR Code enquanto a configuração está pendente", async () => {
    const client = fakeClient();
    client.startMfaEnrollment = vi.fn()
      .mockResolvedValueOnce({
        otpauthUri: "otpauth://totp/example",
        qrImageDataUri: "data:image/png;base64,old",
        manualEntryKey: "OLDKEY",
        pendingExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
      })
      .mockResolvedValueOnce({
        otpauthUri: "otpauth://totp/example",
        qrImageDataUri: "data:image/png;base64,new",
        manualEntryKey: "NEWKEY",
        pendingExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
      });

    render(<MfaSettings client={client} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    expect(await screen.findByText("OLDKEY")).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "Gerar novo QR Code" }));

    expect(await screen.findByText("NEWKEY")).toBeDefined();
    expect(client.startMfaEnrollment).toHaveBeenCalledTimes(2);
  });

  it("permite copiar e baixar os códigos de recuperação", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    const createObjectURL = vi.fn(() => "blob:codes");
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, "createObjectURL", { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revokeObjectURL });
    const click = vi.fn();
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
      const element = originalCreateElement(tag);
      if (tag === "a") {
        Object.defineProperty(element, "click", { value: click });
      }
      return element;
    });

    const client = fakeClient();
    render(<MfaSettings client={client} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    fireEvent.change(await screen.findByLabelText("Código de 6 dígitos"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Ativar MFA" }));
    await screen.findByText("CODE0-XXXXX");

    fireEvent.click(screen.getByRole("button", { name: "Copiar códigos" }));
    expect(writeText).toHaveBeenCalledWith(
      Array.from({ length: 10 }, (_, index) => `CODE${index}-XXXXX`).join("\n"),
    );

    fireEvent.click(screen.getByRole("button", { name: "Baixar códigos" }));
    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
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

  it("senha recusada mostra a mensagem e não exibe QR Code nem chave manual", async () => {
    const client = fakeClient({ startMfaEnrollment: vi.fn().mockRejectedValue(new Error("401")) });
    render(<MfaSettings client={client} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "senha errada qualquer" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));

    expect((await screen.findByRole("status")).textContent)
      .toBe("Não foi possível iniciar a configuração. Confira a senha e tente novamente.");
    expect(screen.queryByAltText("QR Code para configurar o aplicativo autenticador")).toBeNull();
    expect(screen.queryByText("JBSWY3DPEHPK3PXP")).toBeNull();
  });

  it("código errado mostra a mensagem e não exibe códigos de recuperação", async () => {
    const client = fakeClient({ confirmMfaEnrollment: vi.fn().mockRejectedValue(new Error("400")) });
    const onComplete = vi.fn();
    render(<MfaSettings client={client} onComplete={onComplete} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    fireEvent.change(await screen.findByLabelText("Código de 6 dígitos"), { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: "Ativar MFA" }));

    expect(await screen.findByText("Código inválido ou expirado. Gere um novo QR Code e tente novamente.")).toBeDefined();
    expect(screen.queryByText("CODE0-XXXXX")).toBeNull();
    expect(onComplete).not.toHaveBeenCalled();
    expect((screen.getByLabelText("Código de 6 dígitos") as HTMLInputElement).value).toBe("");
  });

  it("falha ao gerar novo QR Code volta para a senha e descarta a chave anterior", async () => {
    const start = vi.fn()
      .mockResolvedValueOnce({
        otpauthUri: "otpauth://totp/example",
        qrImageDataUri: "data:image/png;base64,aGVsbG8=",
        manualEntryKey: "JBSWY3DPEHPK3PXP",
        pendingExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
      })
      .mockRejectedValueOnce(new Error("401"));
    render(<MfaSettings client={fakeClient({ startMfaEnrollment: start })} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    fireEvent.click(await screen.findByRole("button", { name: "Gerar novo QR Code" }));

    expect(await screen.findByText("Não foi possível gerar um novo QR Code. Confira a senha e tente novamente.")).toBeDefined();
    expect(screen.queryByText("JBSWY3DPEHPK3PXP")).toBeNull();
    expect((screen.getByLabelText("Senha atual") as HTMLInputElement).value).toBe("");
  });

  it("falha ao copiar orienta copiar manualmente", async () => {
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn().mockRejectedValue(new Error("negado")) },
    });
    render(<MfaSettings client={fakeClient()} onComplete={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Senha atual"), { target: { value: "frase segura de teste" } });
    fireEvent.click(screen.getByRole("button", { name: "Continuar" }));
    fireEvent.change(await screen.findByLabelText("Código de 6 dígitos"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Ativar MFA" }));
    fireEvent.click(await screen.findByRole("button", { name: /Copiar/ }));

    expect(await screen.findByText("Não foi possível copiar. Selecione e copie manualmente.")).toBeDefined();
  });
});
