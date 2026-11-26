import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MonthSummary } from "./MonthSummary";
import type { LedgerSummaryDTO } from "../../lib/api";

afterEach(cleanup);

const income = { amount: "5000.00", currency: "BRL" as const, effectiveFrom: "2026-08", changedAt: "2026-08-10T12:00:00Z" };

const summary = {
  income,
  allocated: { amount: "4250.00", currency: "BRL" },
  unallocated: { amount: "750.00", currency: "BRL" },
  usagePct: 85,
  envelopes: [],
} as unknown as LedgerSummaryDTO;

describe("MonthSummary", () => {
  it("reúne renda, reservado, não alocado e uso em um único bloco", () => {
    render(<MonthSummary summary={summary} income={income} month="2026-08" monthLabel="Agosto 2026" />);

    const region = screen.getByRole("region", { name: /Resumo do mês/i });
    expect(within(region).getAllByText(/R\$\s*5\.000,00/)).toHaveLength(1);
    expect(within(region).getByText(/R\$\s*4\.250,00/)).toBeDefined();
    expect(within(region).getByText(/R\$\s*750,00/)).toBeDefined();
    expect(within(region).getByText("85%")).toBeDefined();
    expect(within(region).getByText(/Vigência 2026-08/)).toBeDefined();
  });

  it("indica quando a renda é de mês anterior e vale para o mês consultado", () => {
    render(
      <MonthSummary
        summary={null}
        income={{ ...income, amount: "4100.25", effectiveFrom: "2026-07" }}
        month="2026-08"
        monthLabel="Agosto 2026"
      />,
    );

    expect(screen.getByText(/vale para 2026-08/)).toBeDefined();
  });

  it("oferece configurar renda uma única vez quando não há renda", () => {
    const onConfigureIncome = vi.fn();
    render(
      <MonthSummary
        summary={null}
        income={null}
        month="2026-08"
        monthLabel="Agosto 2026"
        onConfigureIncome={onConfigureIncome}
      />,
    );

    expect(screen.getByText("Renda não configurada")).toBeDefined();
    expect(screen.getAllByRole("button", { name: "Configurar renda" })).toHaveLength(1);
  });

  it("não oferece o controle de renda quando a sessão não pode configurá-la", () => {
    render(<MonthSummary summary={summary} income={income} month="2026-08" monthLabel="Agosto 2026" />);

    expect(screen.queryByRole("button", { name: /renda/i })).toBeNull();
  });

  it("erro de carregamento aparece como alerta no lugar da vigência", () => {
    render(<MonthSummary summary={null} income={null} month="2026-08" monthLabel="Agosto 2026" error="Não foi possível carregar o resumo." />);

    expect(screen.getByRole("alert").textContent).toBe("Não foi possível carregar o resumo.");
    expect(screen.queryByText(/sem renda efetiva/)).toBeNull();
  });

  it("erro com resumo antigo mantém o alerta visível", () => {
    render(<MonthSummary summary={summary} income={income} month="2026-08" monthLabel="Agosto 2026" error="Sessão expirada." />);

    expect(screen.getByRole("alert").textContent).toBe("Sessão expirada.");
    expect(screen.queryByText(/Vigência 2026-08/)).toBeNull();
  });

  it("sem renda efetiva mostra o mês sem renda e traços nos indicadores", () => {
    render(<MonthSummary summary={null} income={null} month="2026-08" monthLabel="Agosto 2026" />);

    expect(screen.getByText("Agosto 2026 — sem renda efetiva")).toBeDefined();
    expect(screen.getAllByText("—")).toHaveLength(3);
    expect(screen.queryByText(/Atualizado em/)).toBeNull();
  });

  it("carregando não mostra valor de renda antigo", () => {
    render(<MonthSummary summary={summary} income={income} month="2026-08" monthLabel="Agosto 2026" loading />);

    expect(screen.getByText("Carregando…")).toBeDefined();
    expect(screen.getByRole("region", { name: /Resumo do mês/i }).getAttribute("aria-busy")).toBe("true");
  });

  it("resumo sem renda não calcula percentual de uso", () => {
    const withoutIncome = { ...summary, income: null, usagePct: 0 } as unknown as LedgerSummaryDTO;
    render(<MonthSummary summary={withoutIncome} income={null} month="2026-08" monthLabel="Agosto 2026" />);

    const region = screen.getByRole("region", { name: /Resumo do mês/i });
    expect(within(region).queryByText("0%")).toBeNull();
    expect(within(region).getByText("Renda não configurada")).toBeDefined();
  });

  it("valores zerados aparecem com duas casas", () => {
    const zero = {
      ...summary,
      allocated: { amount: "0.00", currency: "BRL" },
      unallocated: { amount: "0.00", currency: "BRL" },
      usagePct: 0,
    } as unknown as LedgerSummaryDTO;
    render(<MonthSummary summary={zero} income={income} month="2026-08" monthLabel="Agosto 2026" />);

    expect(screen.getAllByText(/R\$\s*0,00/)).toHaveLength(2);
    expect(screen.getByText("0%")).toBeDefined();
  });
});
