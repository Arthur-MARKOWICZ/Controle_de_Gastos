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
});
