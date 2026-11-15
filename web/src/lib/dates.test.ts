import { afterEach, describe, expect, it, vi } from "vitest";
import {
  addDays,
  currentMonthSaoPaulo,
  formatDateLabel,
  formatMonthLabel,
  isFutureDate,
  parseMonthParam,
  todaySaoPaulo,
} from "./dates";

afterEach(() => {
  vi.useRealTimers();
});

describe("datas no fuso de São Paulo", () => {
  it("usa o dia de São Paulo mesmo quando em UTC já é o dia seguinte", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-16T01:30:00Z"));

    expect(todaySaoPaulo()).toBe("2026-09-15");
    expect(currentMonthSaoPaulo()).toBe("2026-09");
  });

  it("aceita mês válido no parâmetro da URL", () => {
    expect(parseMonthParam("2026-12")).toBe("2026-12");
  });

  it("recusa mês 13, mês sem zero e lixo no parâmetro", () => {
    expect(parseMonthParam("2026-13")).toBeNull();
    expect(parseMonthParam("2026-9")).toBeNull();
    expect(parseMonthParam("2026-09'; drop")).toBeNull();
  });

  it("parâmetro vazio ou ausente vira null", () => {
    expect(parseMonthParam("")).toBeNull();
    expect(parseMonthParam(null)).toBeNull();
    expect(parseMonthParam(undefined)).toBeNull();
  });

  it("formata o mês por extenso em pt-BR", () => {
    expect(formatMonthLabel("2026-09")).toMatch(/setembro de 2026/i);
  });

  it("devolve o texto original quando o mês é inválido", () => {
    expect(formatMonthLabel("set/2026")).toBe("set/2026");
  });

  it("considera futuro só o que passa de hoje", () => {
    expect(isFutureDate("2026-09-16", "2026-09-15")).toBe(true);
    expect(isFutureDate("2026-09-15", "2026-09-15")).toBe(false);
    expect(isFutureDate("2026-09-14", "2026-09-15")).toBe(false);
  });

  it("soma dias atravessando o fim do mês e do ano", () => {
    expect(addDays("2026-09-30", 1)).toBe("2026-10-01");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
  });

  it("formata data ISO como dd/mm/aaaa sem deslocar o dia pelo fuso", () => {
    expect(formatDateLabel("2026-09-01")).toBe("01/09/2026");
  });

  it("devolve a data original quando ela não é ISO", () => {
    expect(formatDateLabel("ontem")).toBe("ontem");
  });
});
