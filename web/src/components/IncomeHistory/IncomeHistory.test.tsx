import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { IncomeHistory } from "./IncomeHistory";

afterEach(cleanup);

describe("IncomeHistory", () => {
  it("lista itens com valor e vigência", () => {
    render(<IncomeHistory data={{ items: [{ id: "1", amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString(), changedBy: "u1" }, { id: "2", amount: "4100.25", currency: "BRL", effectiveFrom: "2026-07", changedAt: new Date().toISOString(), changedBy: "u1" }], page: 0, size: 20, hasNext: false }} loading={false} error={null} />);
    expect(screen.getAllByText(/5\.000,00/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/4\.100,25/)).toBeDefined();
  });

  it("mostra carregar mais quando hasNext", () => {
    render(<IncomeHistory data={{ items: [{ id: "1", amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString(), changedBy: "u1" }], page: 0, size: 1, hasNext: true }} loading={false} error={null} onLoadMore={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Carregar mais" })).toBeDefined();
  });

  it("mostra estado vazio quando sem histórico", () => {
    render(<IncomeHistory data={{ items: [], page: 0, size: 20, hasNext: false }} loading={false} error={null} />);
    expect(screen.getByText(/Nenhuma alteração/)).toBeDefined();
  });
});
