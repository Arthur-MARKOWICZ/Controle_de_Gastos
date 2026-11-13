import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { LedgerSummaryDTO } from "../../lib/api";
import { BalanceSummary } from "./BalanceSummary";

afterEach(cleanup);

const brl = (amount: string) => ({ amount, currency: "BRL" as const });

function summary(overrides: Partial<LedgerSummaryDTO> = {}): LedgerSummaryDTO {
  return {
    income: { ...brl("5000.00"), effectiveFrom: "2026-09", changedAt: "2026-09-01T00:00:00Z" },
    allocated: brl("1250.50"),
    unallocated: brl("3749.50"),
    usagePct: 25.01,
    envelopes: [],
    ...overrides,
  };
}

function stat(label: string) {
  return within(screen.getByText(label).parentElement as HTMLElement).getByRole("definition").textContent;
}

describe("BalanceSummary", () => {
  it("mostra renda, reservado, não alocado e uso da renda", () => {
    render(<BalanceSummary summary={summary()} monthLabel="setembro de 2026" />);

    expect(screen.getByText(/R\$\s?5\.000,00/)).toBeDefined();
    expect(stat("Já reservado")).toMatch(/1\.250,50/);
    expect(stat("Não alocado")).toMatch(/3\.749,50/);
    expect(stat("Uso da renda")).toBe("25%");
    expect(screen.getByText("Vigência 2026-09")).toBeDefined();
  });

  it("enquanto carrega mostra traços em vez de valores", () => {
    render(<BalanceSummary summary={null} monthLabel="setembro de 2026" />);

    expect(stat("Já reservado")).toBe("—");
    expect(stat("Uso da renda")).toBe("—");
    expect(screen.getByText("setembro de 2026")).toBeDefined();
  });

  it("sem renda configurada avisa e não inventa percentual", () => {
    render(<BalanceSummary summary={summary({ income: null, usagePct: 0 })} monthLabel="setembro de 2026" />);

    expect(screen.getByText("Renda não configurada")).toBeDefined();
    expect(stat("Uso da renda")).toBe("—");
  });

  it("não alocado negativo aparece com sinal de menos", () => {
    render(<BalanceSummary summary={summary({ unallocated: brl("-10.00"), usagePct: 100.2 })} monthLabel="x" />);

    expect(stat("Não alocado")).toMatch(/-.*10,00/);
    expect(stat("Uso da renda")).toBe("100%");
  });

  it("arredonda o uso da renda para inteiro", () => {
    render(<BalanceSummary summary={summary({ usagePct: 33.5 })} monthLabel="x" />);

    expect(stat("Uso da renda")).toBe("34%");
  });

  it("valor inválido vindo da API é exibido como veio, sem virar NaN", () => {
    render(<BalanceSummary summary={summary({ allocated: brl("abc") })} monthLabel="x" />);

    expect(stat("Já reservado")).toBe("abc");
  });

  it("zero alocado mostra R$ 0,00", () => {
    render(<BalanceSummary summary={summary({ allocated: brl("0.00"), usagePct: 0 })} monthLabel="x" />);

    expect(stat("Já reservado")).toMatch(/0,00/);
    expect(stat("Uso da renda")).toBe("0%");
  });
});
