import { act, renderHook } from "@testing-library/react";
import { isValidElement } from "react";
import { describe, expect, it, vi } from "vitest";
import { envelope } from "../lib/fixtures";
import { useExpenseCapture } from "./useExpenseCapture";

function press(init: KeyboardEventInit) {
  act(() => { window.dispatchEvent(new KeyboardEvent("keydown", { cancelable: true, ...init })); });
}

function targetOf(dialog: unknown): string | undefined {
  return isValidElement<{ envelope: { id: string } }>(dialog) ? dialog.props.envelope.id : undefined;
}

describe("useExpenseCapture", () => {
  it("Ctrl+N abre o gasto na primeira verba gastável", () => {
    const { result } = renderHook(() => useExpenseCapture([envelope({ id: "a" }), envelope({ id: "b" })], vi.fn()));

    press({ key: "n", ctrlKey: true });

    expect(targetOf(result.current.dialog)).toBe("a");
  });

  it("Cmd+N no macOS também abre", () => {
    const { result } = renderHook(() => useExpenseCapture([envelope({ id: "a" })], vi.fn()));

    press({ key: "N", metaKey: true });

    expect(targetOf(result.current.dialog)).toBe("a");
  });

  it("abre para a verba escolhida na tela", () => {
    const b = envelope({ id: "b" });
    const { result } = renderHook(() => useExpenseCapture([envelope({ id: "a" }), b], vi.fn()));

    act(() => result.current.openFor(b));

    expect(targetOf(result.current.dialog)).toBe("b");
  });

  it("verba encerrada não é gastável e o atalho não abre nada", () => {
    const { result } = renderHook(() =>
      useExpenseCapture([envelope({ id: "a", archivedAt: "2026-10-01T00:00:00Z" })], vi.fn()));

    press({ key: "n", ctrlKey: true });

    expect(result.current.hasSpendable).toBe(false);
    expect(result.current.dialog).toBeNull();
  });

  it("N sem modificador não abre o gasto", () => {
    const { result } = renderHook(() => useExpenseCapture([envelope({ id: "a" })], vi.fn()));

    press({ key: "n" });

    expect(result.current.dialog).toBeNull();
  });

  it("atalho com o diálogo aberto não troca a verba em uso", () => {
    const b = envelope({ id: "b" });
    const { result } = renderHook(() => useExpenseCapture([envelope({ id: "a" }), b], vi.fn()));
    act(() => result.current.openFor(b));

    press({ key: "n", ctrlKey: true });

    expect(targetOf(result.current.dialog)).toBe("b");
  });

  it("atalho impede o Ctrl+N do navegador de abrir outra janela", () => {
    renderHook(() => useExpenseCapture([envelope({ id: "a" })], vi.fn()));
    const event = new KeyboardEvent("keydown", { key: "n", ctrlKey: true, cancelable: true });

    act(() => { window.dispatchEvent(event); });

    expect(event.defaultPrevented).toBe(true);
  });
});
