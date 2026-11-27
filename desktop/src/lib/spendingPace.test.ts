import { describe, expect, it } from "vitest";
import { spendingPaceLabel } from "./spendingPace";
import { paceRow } from "./fixtures";

describe("spendingPaceLabel", () => {
  it("usa o dia de estouro quando a API informa", () => {
    expect(spendingPaceLabel(paceRow())).toBe("Estouro projetado em 20/10/2026");
  });

  it("usa a allowance diária quando não há estouro", () => {
    expect(spendingPaceLabel(paceRow({
      projectedOverrunDay: null,
      dailyRemainingAllowance: "9.55",
    }))).toMatch(/R\$\s*9,55 por dia até o fim do mês/);
  });
});
