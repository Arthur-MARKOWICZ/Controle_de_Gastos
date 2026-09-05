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
});
