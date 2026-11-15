import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { EnvelopeDTO } from "../../lib/api";
import { PurposeSection } from "./PurposeSection";

vi.mock("../EnvelopeCard/EnvelopeCard", () => ({
  EnvelopeCard: ({ envelope }: { envelope: EnvelopeDTO }) => <li>{envelope.name}</li>,
}));

afterEach(cleanup);

const brl = (amount: string) => ({ amount, currency: "BRL" as const });

function envelope(id: string, base: string, available: string): EnvelopeDTO {
  return {
    id, ownerId: "o", name: `Verba ${id}`, purpose: "LIMIT", baseAmount: brl(base), targetAmount: null, targetReachedAt: null,
    available: brl(available), isNegative: available.startsWith("-"), role: "OWNER", wealthAllocation: false,
    createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
  };
}

function setup(envelopes: EnvelopeDTO[]) {
  const handlers = { onRegisterExpense: vi.fn(), onManageParticipants: vi.fn(), onArchive: vi.fn(), onToggleWealthAllocation: vi.fn(), onCreate: vi.fn() };
  render(<PurposeSection purpose="LIMIT" envelopes={envelopes} {...handlers} />);
  return handlers;
}

describe("PurposeSection", () => {
  it("soma disponível e base das verbas da seção", () => {
    setup([envelope("a", "100.00", "40.00"), envelope("b", "50.00", "50.00")]);

    expect(screen.getByText(/90,00/)).toBeDefined();
    expect(screen.getByText(/de R\$\s?150,00 · 2 verbas/)).toBeDefined();
  });

  it("soma centavos sem erro de ponto flutuante visível", () => {
    setup([envelope("a", "0.10", "0.10"), envelope("b", "0.20", "0.20")]);

    expect(screen.getByText(/de R\$\s?0,30 · 2 verbas/)).toBeDefined();
  });

  it("conta as verbas negativas", () => {
    setup([envelope("a", "100.00", "-5.00"), envelope("b", "100.00", "-0.01")]);

    expect(screen.getByText("2 negativa(s)")).toBeDefined();
  });

  it("seção vazia mostra estado vazio e traço no total", () => {
    setup([]);

    expect(screen.getByRole("status").textContent).toBe("Nenhuma verba de limite de gasto neste mês.");
    expect(screen.getByText("—")).toBeDefined();
  });

  it("singular para uma verba", () => {
    setup([envelope("a", "10.00", "10.00")]);

    expect(screen.getByText(/1 verba$/)).toBeDefined();
  });

  it("botão de criar avisa a natureza da seção", () => {
    const handlers = setup([]);

    fireEvent.click(screen.getByRole("button", { name: "Criar limite de gasto" }));

    expect(handlers.onCreate).toHaveBeenCalledWith("LIMIT");
  });

  it("lista cada verba da seção", () => {
    setup([envelope("a", "1.00", "1.00"), envelope("b", "1.00", "1.00")]);

    expect(screen.getAllByRole("listitem").map((li) => li.textContent)).toEqual(["Verba a", "Verba b"]);
  });
});
