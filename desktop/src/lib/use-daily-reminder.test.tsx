import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useDailyReminder } from "./use-daily-reminder";

describe("useDailyReminder", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-24T21:59:30Z")); // 18:59:30 em São Paulo
  });
  afterEach(() => vi.useRealTimers());

  const deps = () => ({ hasExpenseToday: vi.fn().mockResolvedValue(false), notify: vi.fn().mockResolvedValue(undefined) });

  it("com o lembrete ativo e sessão aberta, dispara no horário", async () => {
    const d = deps();
    renderHook(() => useDailyReminder({ enabled: true, time: "19:00" }, d));

    await vi.advanceTimersByTimeAsync(30_000);

    expect(d.notify).toHaveBeenCalledTimes(1);
  });

  it("desligado, não agenda", async () => {
    const d = deps();
    renderHook(() => useDailyReminder({ enabled: false, time: "19:00" }, d));

    await vi.advanceTimersByTimeAsync(30_000);

    expect(d.hasExpenseToday).not.toHaveBeenCalled();
  });

  it("ao sair da conta (desmontar), cancela o lembrete", async () => {
    const d = deps();
    const { unmount } = renderHook(() => useDailyReminder({ enabled: true, time: "19:00" }, d));

    unmount();
    await vi.advanceTimersByTimeAsync(30_000);

    expect(d.hasExpenseToday).not.toHaveBeenCalled();
  });

  it("quem já lançou gasto hoje não é lembrado", async () => {
    const d = { hasExpenseToday: vi.fn().mockResolvedValue(true), notify: vi.fn().mockResolvedValue(undefined) };
    renderHook(() => useDailyReminder({ enabled: true, time: "19:00" }, d));

    await vi.advanceTimersByTimeAsync(30_000);

    expect(d.hasExpenseToday).toHaveBeenCalledTimes(1);
    expect(d.notify).not.toHaveBeenCalled();
  });

  it("trocar o horário cancela o agendamento antigo", async () => {
    const d = deps();
    const { rerender } = renderHook(({ time }) => useDailyReminder({ enabled: true, time }, d), {
      initialProps: { time: "19:00" },
    });

    rerender({ time: "19:30" });
    await vi.advanceTimersByTimeAsync(30_000);
    expect(d.notify).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(30 * 60_000);
    expect(d.notify).toHaveBeenCalledTimes(1);
  });

  it("usa as dependências mais recentes sem reagendar", async () => {
    const first = deps();
    const second = deps();
    const { rerender } = renderHook(({ d }) => useDailyReminder({ enabled: true, time: "19:00" }, d), {
      initialProps: { d: first },
    });

    rerender({ d: second });
    await vi.advanceTimersByTimeAsync(30_000);

    expect(first.notify).not.toHaveBeenCalled();
    expect(second.notify).toHaveBeenCalledTimes(1);
  });

  it("falha ao consultar a API não avisa e tenta de novo no dia seguinte", async () => {
    const d = { hasExpenseToday: vi.fn().mockRejectedValue(new Error("sem sessão")), notify: vi.fn() };
    renderHook(() => useDailyReminder({ enabled: true, time: "19:00" }, d));

    await vi.advanceTimersByTimeAsync(30_000);
    expect(d.notify).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(24 * 60 * 60_000);
    expect(d.hasExpenseToday).toHaveBeenCalledTimes(2);
  });

  it("falha ao notificar não derruba o agendamento do dia seguinte", async () => {
    const d = { hasExpenseToday: vi.fn().mockResolvedValue(false), notify: vi.fn().mockRejectedValue(new Error("bloqueado")) };
    renderHook(() => useDailyReminder({ enabled: true, time: "19:00" }, d));

    await vi.advanceTimersByTimeAsync(30_000);
    await vi.advanceTimersByTimeAsync(24 * 60 * 60_000);

    expect(d.notify).toHaveBeenCalledTimes(2);
  });
});
