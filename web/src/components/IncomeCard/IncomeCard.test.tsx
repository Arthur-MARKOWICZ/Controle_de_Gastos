import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { IncomeCard } from "./IncomeCard";

afterEach(cleanup);

describe("IncomeCard", () => {
  it("exibe renda efetiva e vigência com At Atualizado", () => {
    render(<IncomeCard income={{ amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date("2026-08-10T12:00:00Z").toISOString() }} month="2026-08" />);
    expect(screen.getByRole("heading", { name: "Renda do mês" })).toBeDefined();
    expect(screen.getAllByText(/5\.000,00/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Vigência 2026-08/)).toBeDefined();
  });

  it("indica quando renda é de mês anterior (vale para mês consultado)", () => {
    render(<IncomeCard income={{ amount: "4100.25", currency: "BRL", effectiveFrom: "2026-07", changedAt: new Date().toISOString() }} month="2026-08" />);
    expect(screen.getByText(/vale para 2026-08/)).toBeDefined();
  });

  it("mostra CTA quando não configurada", () => {
    const onEdit = vi.fn();
    render(<IncomeCard income={null} month="2026-08" onEdit={onEdit} />);
    expect(screen.getByText(/Renda não configurada/)).toBeDefined();
    expect(screen.getByRole("button", { name: /Configurar renda/ })).toBeDefined();
  });
});
