import { act, renderHook, waitFor } from "@testing-library/react";
import { isValidElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { envelope } from "../lib/fixtures";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";
import { useExpenseCapture } from "./useExpenseCapture";

afterEach(resetIpc);

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

  it("Ctrl+N aplica a primeira sugestão quando a API responde", async () => {
    const almoco = envelope({ id: "env-almoco", name: "Almoço" });
    const mercado = envelope({ id: "env-mercado", name: "Mercado" });
    installFakeIpc().onApi("/api/v1/ledger/expense-suggestions", {
      status: 200,
      body: {
        asOf: "2026-11-27T12:00:00-03:00",
        items: [{ envelopeId: "env-almoco", name: "Almoço", suggestedAmount: { amount: "32.50", currency: "BRL" } }],
      },
    });
    const { result } = renderHook(() => useExpenseCapture([mercado, almoco], vi.fn()));

    press({ key: "n", ctrlKey: true });

    await waitFor(() => expect(targetOf(result.current.dialog)).toBe("env-almoco"));
    const dialog = result.current.dialog;
    expect(isValidElement<{ initialAmount?: string | null }>(dialog) ? dialog.props.initialAmount : null)
      .toBe("32.50");
  });

  it("sugestões vazias mantêm a primeira verba spendable sem valor", async () => {
    installFakeIpc().onApi("/api/v1/ledger/expense-suggestions", {
      status: 200,
      body: { asOf: "2026-11-27T12:00:00-03:00", items: [] },
    });
    const { result } = renderHook(() =>
      useExpenseCapture([envelope({ id: "a" }), envelope({ id: "b" })], vi.fn()),
    );

    press({ key: "n", ctrlKey: true });

    await waitFor(() => expect(targetOf(result.current.dialog)).toBe("a"));
    const dialog = result.current.dialog;
    expect(isValidElement<{ initialAmount?: string | null }>(dialog) ? dialog.props.initialAmount : "x")
      .toBeNull();
  });

  it("openFor preenche o valor da verba forçada quando a API sugere", async () => {
    const b = envelope({ id: "env-b", name: "Farmácia" });
    installFakeIpc().onApi("/api/v1/ledger/expense-suggestions", {
      status: 200,
      body: {
        asOf: "2026-11-27T12:00:00-03:00",
        items: [
          { envelopeId: "env-a", name: "Outra", suggestedAmount: { amount: "10.00", currency: "BRL" } },
          { envelopeId: "env-b", name: "Farmácia", suggestedAmount: { amount: "45.90", currency: "BRL" } },
        ],
      },
    });
    const { result } = renderHook(() =>
      useExpenseCapture([envelope({ id: "env-a" }), b], vi.fn()),
    );

    act(() => result.current.openFor(b));

    await waitFor(() => {
      const dialog = result.current.dialog;
      expect(
        isValidElement<{ initialAmount?: string | null }>(dialog)
          ? dialog.props.initialAmount
          : "x",
      ).toBe("45.90");
    });
    expect(targetOf(result.current.dialog)).toBe("env-b");
  });

  it("openFor sem match na sugestão mantém a verba sem valor", async () => {
    const b = envelope({ id: "env-b" });
    installFakeIpc().onApi("/api/v1/ledger/expense-suggestions", {
      status: 200,
      body: {
        asOf: "2026-11-27T12:00:00-03:00",
        items: [
          { envelopeId: "env-a", name: "Outra", suggestedAmount: { amount: "10.00", currency: "BRL" } },
        ],
      },
    });
    const { result } = renderHook(() =>
      useExpenseCapture([envelope({ id: "env-a" }), b], vi.fn()),
    );

    act(() => result.current.openFor(b));

    await waitFor(() => expect(targetOf(result.current.dialog)).toBe("env-b"));
    await act(async () => {
      await Promise.resolve();
    });
    const dialog = result.current.dialog;
    expect(
      isValidElement<{ initialAmount?: string | null }>(dialog)
        ? dialog.props.initialAmount
        : "x",
    ).toBeNull();
  });

  it("ao registrar, mostra toast com o nome da verba", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const onRegistered = vi.fn();
    const { result } = renderHook(() =>
      useExpenseCapture([envelope({ id: "a", name: "Combustível" })], onRegistered),
    );

    act(() => result.current.openFor(envelope({ id: "a", name: "Combustível" })));

    const dialog = result.current.dialog;
    expect(isValidElement(dialog)).toBe(true);
    await act(async () => {
      if (isValidElement<{ onRegistered: () => void }>(dialog)) {
        dialog.props.onRegistered();
      }
    });

    expect(onRegistered).toHaveBeenCalled();
    expect(result.current.toast).not.toBeNull();
    expect(
      isValidElement<{ children?: unknown }>(result.current.toast)
        ? result.current.toast.props.children
        : null,
    ).toBe("Gasto registrado em Combustível.");

    act(() => {
      vi.advanceTimersByTime(4000);
    });
    expect(result.current.toast).toBeNull();
    vi.useRealTimers();
  });
});
