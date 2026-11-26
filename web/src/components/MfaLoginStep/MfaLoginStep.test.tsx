import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MfaLoginStep } from "./MfaLoginStep";

afterEach(cleanup);

describe("MfaLoginStep", () => {
  it("verifica o código do aplicativo autenticador por padrão", async () => {
    const onVerify = vi.fn().mockResolvedValue(undefined);
    render(<MfaLoginStep onVerify={onVerify} onUseRecoveryCode={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Código de 6 dígitos"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));

    await vi.waitFor(() => expect(onVerify).toHaveBeenCalledWith("123456"));
  });

  it("alterna para código de recuperação e usa o outro caminho", async () => {
    const onUseRecoveryCode = vi.fn().mockResolvedValue(undefined);
    render(<MfaLoginStep onVerify={vi.fn()} onUseRecoveryCode={onUseRecoveryCode} />);

    fireEvent.click(screen.getByRole("button", { name: "Código de recuperação" }));
    fireEvent.change(screen.getByLabelText("Código de recuperação"), { target: { value: "ABCDE-FGHJK" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));

    await vi.waitFor(() => expect(onUseRecoveryCode).toHaveBeenCalledWith("ABCDE-FGHJK"));
  });

  it("mostra a mensagem genérica de erro para qualquer falha", async () => {
    const onVerify = vi.fn().mockRejectedValue(new Error("boom"));
    render(<MfaLoginStep onVerify={onVerify} onUseRecoveryCode={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Código de 6 dígitos"), { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));

    expect((await screen.findByRole("status")).textContent)
      .toBe("Não foi possível concluir a autenticação. Tente novamente.");
  });

  it("código errado não chama o caminho de recuperação e limpa o campo", async () => {
    const onVerify = vi.fn().mockRejectedValue(new Error("401"));
    const onUseRecoveryCode = vi.fn();
    render(<MfaLoginStep onVerify={onVerify} onUseRecoveryCode={onUseRecoveryCode} />);

    fireEvent.change(screen.getByLabelText("Código de 6 dígitos"), { target: { value: "999999" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));

    await screen.findByRole("status");
    expect(onUseRecoveryCode).not.toHaveBeenCalled();
    expect((screen.getByLabelText("Código de 6 dígitos") as HTMLInputElement).value).toBe("");
  });

  it("código de recuperação recusado mostra a mesma mensagem genérica, sem revelar o motivo", async () => {
    const onUseRecoveryCode = vi.fn().mockRejectedValue(new Error("código já usado"));
    render(<MfaLoginStep onVerify={vi.fn()} onUseRecoveryCode={onUseRecoveryCode} />);

    fireEvent.click(screen.getByRole("button", { name: "Código de recuperação" }));
    fireEvent.change(screen.getByLabelText("Código de recuperação"), { target: { value: "ABCDE-FGHJK" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));

    const status = await screen.findByRole("status");
    expect(status.textContent).toBe("Não foi possível concluir a autenticação. Tente novamente.");
    expect(status.textContent).not.toContain("já usado");
  });

  it("mostra o erro externo recebido do fluxo de login", () => {
    render(<MfaLoginStep onVerify={vi.fn()} onUseRecoveryCode={vi.fn()} externalError="Desafio expirado. Entre de novo." />);

    expect(screen.getByRole("status").textContent).toBe("Desafio expirado. Entre de novo.");
  });

  it("trocar de método apaga o código digitado e a mensagem anterior", async () => {
    const onVerify = vi.fn().mockRejectedValue(new Error("401"));
    render(<MfaLoginStep onVerify={onVerify} onUseRecoveryCode={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Código de 6 dígitos"), { target: { value: "111111" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));
    await screen.findByRole("status");

    fireEvent.change(screen.getByLabelText("Código de 6 dígitos"), { target: { value: "12" } });
    fireEvent.click(screen.getByRole("button", { name: "Código de recuperação" }));

    expect((screen.getByLabelText("Código de recuperação") as HTMLInputElement).value).toBe("");
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("enquanto verifica, o botão fica desabilitado e não envia duas vezes", async () => {
    let finish: () => void = () => undefined;
    const onVerify = vi.fn(() => new Promise<void>((resolve) => { finish = resolve; }));
    render(<MfaLoginStep onVerify={onVerify} onUseRecoveryCode={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Código de 6 dígitos"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar" }));
    const busy = await screen.findByRole("button", { name: "Verificando…" });
    fireEvent.click(busy);

    expect((busy as HTMLButtonElement).disabled).toBe(true);
    expect(onVerify).toHaveBeenCalledOnce();
    finish();
  });
});
