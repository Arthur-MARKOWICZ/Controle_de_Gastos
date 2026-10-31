import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExpenseForm } from "./ExpenseForm";
import type { EnvelopeDTO } from "../../lib/api";

const request = vi.hoisted(() => vi.fn());

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: { request } }),
}));

const envelope: EnvelopeDTO = {
  id: "env-1", ownerId: "owner", name: "Combustível", purpose: "LIMIT",
  baseAmount: { amount: "400.00", currency: "BRL" }, available: { amount: "240.00", currency: "BRL" },
  isNegative: false, role: "OWNER", createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
} as EnvelopeDTO;

function typeAmount(digits: string) {
  fireEvent.change(screen.getByLabelText("Valor (BRL)"), { target: { value: digits } });
}

afterEach(() => {
  cleanup();
  request.mockReset();
});

describe("ExpenseForm", () => {
  it("mostra o saldo que restará após o gasto", () => {
    render(<ExpenseForm envelope={envelope} onSuccess={vi.fn()} onCancel={vi.fn()} />);

    typeAmount("5000");

    expect(screen.getByText(/Saldo após o gasto:\s*R\$\s*190,00/)).toBeDefined();
    expect(screen.queryByText(/saldo negativo/i)).toBeNull();
  });

  it("avisa, sem bloquear, quando o gasto deixa a verba negativa", async () => {
    request.mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve({ id: "e1" }) });
    const onSuccess = vi.fn();
    render(<ExpenseForm envelope={envelope} onSuccess={onSuccess} onCancel={vi.fn()} />);

    typeAmount("30050");

    expect(screen.getByText(/Saldo após o gasto:\s*-R\$\s*60,50/)).toBeDefined();
    expect(screen.getByText(/verba ficará com saldo negativo/i)).toBeDefined();
    expect((screen.getByRole("button", { name: "Registrar gasto" }) as HTMLButtonElement).disabled).toBe(false);

    fireEvent.submit(screen.getByLabelText("Valor (BRL)").closest("form")!);

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ targetJustReached: false, balanceAfter: "-60.50" }));
  });

  it("não mostra prévia de saldo para aporte", async () => {
    request.mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve({ id: "e1", targetJustReached: true }) });
    const onSuccess = vi.fn();
    render(<ExpenseForm envelope={{ ...envelope, purpose: "GOAL" }} kind="CONTRIBUTION" onSuccess={onSuccess} onCancel={vi.fn()} />);

    typeAmount("5000");
    expect(screen.queryByText(/Saldo após o gasto/)).toBeNull();

    fireEvent.submit(screen.getByLabelText("Valor (BRL)").closest("form")!);

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ targetJustReached: true, balanceAfter: null }));
  });
});
