import { describe, expect, it } from "vitest";
import { spendingPaceLabel } from "./spendingPace";

describe("spendingPaceLabel", () => {
  it("usa o dia de estouro quando a API informa", () => {
    expect(spendingPaceLabel({
      envelopeId: "e1", name: "Combustível", capacity: "400.00", spent: "200.00",
      projectedMonthSpend: "620.00", dailyRemainingAllowance: "9.09",
      projectedOverrunDay: "2026-10-20", month: "2026-10",
    })).toBe("Estouro projetado em 20/10/2026");
  });

  it("usa a allowance diária quando não há estouro", () => {
    expect(spendingPaceLabel({
      envelopeId: "e1", name: "Lazer", capacity: "310.00", spent: "100.00",
      projectedMonthSpend: "310.00", dailyRemainingAllowance: "9.55",
      projectedOverrunDay: null, month: "2026-10",
    })).toMatch(/R\$\s*9,55 por dia até o fim do mês/);
  });
});
