import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EnvelopeCard } from "./EnvelopeCard";

describe("EnvelopeCard", () => {
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
