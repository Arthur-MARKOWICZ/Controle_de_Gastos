import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { millisUntilNextReminder, startDailyReminder } from "./reminder";

/** 2026-11-24 18:59:30 em São Paulo (UTC-3). */
const BEFORE_SEVEN_PM = new Date("2026-11-24T21:59:30Z");

describe("millisUntilNextReminder", () => {
  it("conta até o horário de hoje em São Paulo, não no fuso da máquina", () => {
    expect(millisUntilNextReminder(BEFORE_SEVEN_PM, "19:00")).toBe(30_000);
  });

  it("horário já passado ou exato vai para amanhã", () => {
    expect(millisUntilNextReminder(new Date("2026-11-24T22:00:00Z"), "19:00")).toBe(24 * 60 * 60 * 1000);
    expect(millisUntilNextReminder(new Date("2026-11-25T00:00:00Z"), "19:00")).toBe(22 * 60 * 60 * 1000);
  });
});

describe("startDailyReminder", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(BEFORE_SEVEN_PM);
  });
  afterEach(() => vi.useRealTimers());

  it("no horário, notifica só se não houve gasto hoje", async () => {
    const notify = vi.fn().mockResolvedValue(undefined);
    const hasExpenseToday = vi.fn().mockResolvedValue(false);
    const stop = startDailyReminder("19:00", { hasExpenseToday, notify });

    await vi.advanceTimersByTimeAsync(29_000);
    expect(hasExpenseToday).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1_000);

    expect(hasExpenseToday).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledTimes(1);
    stop();
  });

  it("não notifica quem já lançou e reagenda para o dia seguinte", async () => {
    const notify = vi.fn().mockResolvedValue(undefined);
    const hasExpenseToday = vi.fn().mockResolvedValueOnce(true).mockResolvedValueOnce(false);
    const stop = startDailyReminder("19:00", { hasExpenseToday, notify });

    await vi.advanceTimersByTimeAsync(30_000);
    expect(notify).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(24 * 60 * 60 * 1000);

    expect(hasExpenseToday).toHaveBeenCalledTimes(2);
    expect(notify).toHaveBeenCalledTimes(1);
    stop();
  });

  it("sem sessão ou sem resposta da API, não notifica", async () => {
    const notify = vi.fn();
    const stop = startDailyReminder("19:00", {
      hasExpenseToday: vi.fn().mockRejectedValue(new Error("Sessão expirada")),
      notify,
    });

    await vi.advanceTimersByTimeAsync(30_000);

    expect(notify).not.toHaveBeenCalled();
    stop();
  });

  it("aberto depois do horário, não mostra aviso atrasado", async () => {
    vi.setSystemTime(new Date("2026-11-25T00:00:00Z")); // 21:00 em São Paulo
    const notify = vi.fn();
    const hasExpenseToday = vi.fn().mockResolvedValue(false);
    const stop = startDailyReminder("19:00", { hasExpenseToday, notify });

    await vi.advanceTimersByTimeAsync(60 * 60 * 1000);

    expect(hasExpenseToday).not.toHaveBeenCalled();
    expect(notify).not.toHaveBeenCalled();
    stop();
  });

  it("timer que acorda um pouco antes da hora não gera segundo aviso no mesmo dia", async () => {
    const notify = vi.fn().mockResolvedValue(undefined);
    const hasExpenseToday = vi.fn().mockResolvedValue(false);
    const stop = startDailyReminder("19:00", { hasExpenseToday, notify });

    await vi.advanceTimersByTimeAsync(30_000);
    vi.setSystemTime(new Date("2026-11-24T21:59:59.990Z")); // relógio "volta" 10 ms
    await vi.advanceTimersByTimeAsync(2 * 60 * 1000);

    expect(notify).toHaveBeenCalledTimes(1);
    stop();
  });

  it("parar cancela o disparo pendente", async () => {
    const hasExpenseToday = vi.fn().mockResolvedValue(false);
    const stop = startDailyReminder("19:00", { hasExpenseToday, notify: vi.fn() });

    stop();
    await vi.advanceTimersByTimeAsync(60_000);

    expect(hasExpenseToday).not.toHaveBeenCalled();
  });
});
