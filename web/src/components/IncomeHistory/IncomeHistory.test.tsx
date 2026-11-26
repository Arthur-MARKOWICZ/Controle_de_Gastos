import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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

  it("mostra o erro recebido e oferece tentar novamente", () => {
    const onRetry = vi.fn();
    render(<IncomeHistory data={null} loading={false} error="Não foi possível carregar o histórico de renda." onRetry={onRetry} />);

    expect(screen.getByRole("alert").textContent).toContain("Não foi possível carregar o histórico de renda.");
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("erro com dados antigos esconde a lista em vez de mostrar valores desatualizados", () => {
    render(<IncomeHistory data={page([item("1", "5000.00")], false)} loading={false} error="Sessão expirada." />);

    expect(screen.getByRole("alert").textContent).toContain("Sessão expirada.");
    expect(screen.queryByText(/5\.000,00/)).toBeNull();
  });

  it("erro sem onRetry não oferece botão de nova tentativa", () => {
    render(<IncomeHistory data={null} loading={false} error="Falha de rede." />);

    expect(screen.getByRole("alert").textContent).toContain("Falha de rede.");
    expect(screen.queryByRole("button", { name: "Tentar novamente" })).toBeNull();
  });

  it("carregando sem dados mostra o estado de carregamento e nenhuma lista", () => {
    render(<IncomeHistory data={null} loading error={null} />);

    expect(screen.getByRole("status").textContent).toBe("Carregando histórico…");
    expect(screen.queryByRole("list")).toBeNull();
  });

  it("sem próxima página não mostra carregar mais mesmo com onLoadMore", () => {
    render(<IncomeHistory data={page([item("1", "5000.00")], false)} loading={false} error={null} onLoadMore={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Carregar mais" })).toBeNull();
  });

  it("valor zero aparece com duas casas", () => {
    render(<IncomeHistory data={page([item("1", "0.00")], false)} loading={false} error={null} />);

    expect(screen.getByText(/R\$\s*0,00/)).toBeDefined();
  });
});

function item(id: string, amount: string) {
  return { id, amount, currency: "BRL", effectiveFrom: "2026-08", changedAt: "2026-08-01T12:00:00Z", changedBy: "u1" };
}

function page(items: ReturnType<typeof item>[], hasNext: boolean) {
  return { items, page: 0, size: 20, hasNext };
}
