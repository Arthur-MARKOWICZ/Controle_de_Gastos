import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { EnvelopeDTO } from "../../lib/api";
import { EnvelopeCard } from "./EnvelopeCard";

afterEach(cleanup);

function envelope(role: "OWNER" | "PARTICIPANT"): EnvelopeDTO {
  return {
    id: "e1", ownerId: "u1", name: "Moradia", purpose: "FIXED", baseAmount: { amount: "2000.00", currency: "BRL" },
    targetAmount: null, targetReachedAt: null, available: { amount: "2000.00", currency: "BRL" }, isNegative: false,
    role, wealthAllocation: false, createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
  };
}

describe("EnvelopeCard · recorrências", () => {
  it("oferece Recorrências ao dono", () => {
    const onManageRecurring = vi.fn();
    render(<ul><EnvelopeCard envelope={envelope("OWNER")} onRegisterExpense={vi.fn()} onManageRecurring={onManageRecurring} /></ul>);

    const action = screen.getByRole("button", { name: "Recorrências de Moradia", hidden: true });
    fireEvent.click(action);

    expect(onManageRecurring).toHaveBeenCalled();
  });

  it("não mostra Recorrências ao participante", () => {
    render(<ul><EnvelopeCard envelope={envelope("PARTICIPANT")} onRegisterExpense={vi.fn()} onManageRecurring={vi.fn()} /></ul>);

    expect(screen.queryByRole("button", { name: "Recorrências de Moradia", hidden: true })).toBeNull();
  });
});
