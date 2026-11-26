import { describe, expect, it } from "vitest";
import {
  currentMonthSaoPaulo,
  formatDateLabel,
  formatMonthLabel,
  isValidMonth,
  shiftMonth,
  todaySaoPaulo,
} from "./dates";

describe("datas no calendário de negócio", () => {
  it("hoje segue São Paulo, não o UTC da máquina", () => {
    expect(todaySaoPaulo(new Date("2026-11-26T02:30:00Z"))).toBe("2026-11-25");
  });

  it("virada de ano em UTC ainda é dezembro em São Paulo", () => {
    expect(currentMonthSaoPaulo(new Date("2027-01-01T02:00:00Z"))).toBe("2026-12");
  });

  it("desloca meses atravessando o ano nos dois sentidos", () => {
    expect(shiftMonth("2026-12", 1)).toBe("2027-01");
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
    expect(shiftMonth("2026-11", -23)).toBe("2024-12");
  });

  it("mês inválido não é deslocado nem formatado", () => {
    expect(shiftMonth("2026-13", 1)).toBe("2026-13");
    expect(formatMonthLabel("2026-00")).toBe("2026-00");
  });

  it("aceita só AAAA-MM com mês de 01 a 12", () => {
    expect(isValidMonth("2026-11")).toBe(true);
    expect(isValidMonth("2026-1")).toBe(false);
    expect(isValidMonth("2026-00")).toBe(false);
    expect(isValidMonth("26-11")).toBe(false);
  });

  it("formata mês e data em pt-BR", () => {
    expect(formatMonthLabel("2026-11")).toBe("novembro de 2026");
    expect(formatDateLabel("2026-11-05")).toBe("05/11/2026");
  });

  it("data que não é ISO volta como veio", () => {
    expect(formatDateLabel("ontem")).toBe("ontem");
    expect(formatDateLabel("")).toBe("");
  });
});
