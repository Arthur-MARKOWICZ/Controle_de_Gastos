import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EnvelopeCard } from "./EnvelopeCard";

describe("EnvelopeCard", () => {
  it("mostra o restante e aumenta a barra de uma meta de aporte acumulada", () => {
    const envelope = {
      id: "goal", ownerId: "owner", name: "Investimentos", purpose: "GOAL" as const,
      baseAmount: { amount: "100.00", currency: "BRL" as const }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" as const }, isNegative: false, role: "OWNER" as const,
      createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
      goalProgress: { plannedAmount: { amount: "100.00", currency: "BRL" as const }, contributedAmount: { amount: "0.00", currency: "BRL" as const }, remainingAmount: { amount: "100.00", currency: "BRL" as const }, percent: 0 },
    };
    const { rerender, unmount } = render(<ul><EnvelopeCard variant="verbas" envelope={envelope} /></ul>);

    expect(screen.getAllByText(/Faltam R\$\s*100,00/)).toHaveLength(2);
    expect(screen.getByRole("progressbar", { name: "Progresso de Investimentos" }).getAttribute("value")).toBe("0");

    rerender(<ul><EnvelopeCard variant="verbas" envelope={{ ...envelope, goalProgress: { plannedAmount: { amount: "200.00", currency: "BRL" }, contributedAmount: { amount: "20.00", currency: "BRL" }, remainingAmount: { amount: "180.00", currency: "BRL" }, percent: 10 } }} /></ul>);

    expect(screen.getAllByText(/Faltam R\$\s*180,00/)).toHaveLength(2);
    expect(screen.getByText(/meta acumulada R\$\s*200,00/)).toBeDefined();
    expect(screen.getByRole("progressbar", { name: "Progresso de Investimentos" }).getAttribute("value")).toBe("10");
    unmount();
  });

  it("explica a regra e mostra o progresso de uma meta de acumulação", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "target", ownerId: "owner", name: "Notebook", purpose: "SAVINGS_TARGET",
      baseAmount: { amount: "0.00", currency: "BRL" }, targetAmount: { amount: "1000.00", currency: "BRL" }, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER", createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByText("Meta de acumulação")).toBeDefined();
    expect(screen.getByText(/saldo não reinicia/)).toBeDefined();
    expect(screen.getByText(/10% concluído/)).toBeDefined();
    expect(screen.getByRole("progressbar", { name: "Progresso de Notebook" }).getAttribute("value")).toBe("10");
  });
});
