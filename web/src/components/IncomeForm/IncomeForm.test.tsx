import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { IncomeForm } from "./IncomeForm";

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({
    client: {
      request: vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({ amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() }), status: 200 })),
    },
  }),
}));

afterEach(cleanup);

describe("IncomeForm", () => {
  it("exibe erro quando valor está vazio", async () => {
    const onSuccess = vi.fn();
    render(<IncomeForm onSuccess={onSuccess} onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Salvar renda" }));
    expect(await screen.findByRole("alert")).toBeDefined();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("formata máscara e aceita valor válido", async () => {
    const onSuccess = vi.fn();
    render(<IncomeForm onSuccess={onSuccess} onCancel={vi.fn()} />);
    const input = screen.getByLabelText(/Valor da renda/) as HTMLInputElement;
    fireEvent.change(input, { target: { value: "5000,00" } });
    // after mask, input value becomes 5.000,00
    expect(input.value).toBe("5.000,00");
  });
});
