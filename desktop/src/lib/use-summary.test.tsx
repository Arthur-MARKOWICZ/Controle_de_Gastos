import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { summary } from "./fixtures";
import { installFakeIpc, resetIpc } from "./test-ipc";
import { useSummary } from "./use-summary";

afterEach(resetIpc);

// Callback estável: um vi.fn() novo a cada render dispararia o efeito em laço.
const noop = vi.fn();

const path = (month: string) => `/api/v1/ledger/summary?month=${month}`;

describe("useSummary", () => {
  it("carrega o resumo do mês", async () => {
    installFakeIpc().onApi(path("2026-11"), { status: 200, body: summary() });

    const { result } = renderHook(() => useSummary("2026-11", 0, noop));

    expect(result.current.state.kind).toBe("loading");
    await waitFor(() => expect(result.current.state.kind).toBe("ready"));
  });

  it("recarregar pede o resumo de novo", async () => {
    const ipc = installFakeIpc().onApi(path("2026-11"), { status: 200, body: summary() });
    const { result } = renderHook(() => useSummary("2026-11", 0, noop));
    await waitFor(() => expect(result.current.state.kind).toBe("ready"));

    result.current.reload();

    await waitFor(() => expect(ipc.countPath(path("2026-11"))).toBe(2));
  });

  it("sessão perdida chama o login e não mostra erro na tela", async () => {
    installFakeIpc().on("api", () => { throw { message: "Sessão expirada.", authRequired: true }; });
    const onAuthRequired = vi.fn();

    const { result } = renderHook(() => useSummary("2026-11", 0, onAuthRequired));

    await waitFor(() => expect(onAuthRequired).toHaveBeenCalledOnce());
    expect(result.current.state.kind).toBe("loading");
  });

  it("resposta de um mês antigo não sobrescreve o mês atual", async () => {
    let releaseOld: (value: unknown) => void = () => undefined;
    const old = new Promise((resolve) => { releaseOld = resolve; });
    installFakeIpc().on("api", async (args) => {
      if (String(args.path) === path("2026-10")) {
        await old;
        return { status: 200, body: JSON.stringify(summary({ unallocated: { amount: "1.00", currency: "BRL" } })) };
      }
      return { status: 200, body: JSON.stringify(summary()) };
    });

    const { result, rerender } = renderHook(({ month }) => useSummary(month, 0, noop), {
      initialProps: { month: "2026-10" },
    });
    rerender({ month: "2026-11" });
    await waitFor(() => expect(result.current.state.kind).toBe("ready"));
    releaseOld(undefined);
    await new Promise((resolve) => setTimeout(resolve, 10));

    const state = result.current.state;
    expect(state.kind === "ready" && state.summary.unallocated.amount).toBe("1550.00");
  });

  it("falha da API vira estado de erro com a mensagem", async () => {
    installFakeIpc().onApi(path("2026-11"), { status: 500, body: "" });

    const { result } = renderHook(() => useSummary("2026-11", 0, noop));

    await waitFor(() => expect(result.current.state).toEqual({
      kind: "error",
      message: "A API falhou. Tente de novo em instantes.",
    }));
  });

  it("acesso negado vira estado de erro sem chamar o login", async () => {
    installFakeIpc().onApi(path("2026-11"), { status: 403, body: "" });
    const onAuthRequired = vi.fn();

    const { result } = renderHook(() => useSummary("2026-11", 0, onAuthRequired));

    await waitFor(() => expect(result.current.state).toEqual({ kind: "error", message: "Você não tem acesso a esta verba." }));
    expect(onAuthRequired).not.toHaveBeenCalled();
  });

  it("falha nativa comum vira erro com a mensagem nativa", async () => {
    installFakeIpc().on("api", () => { throw { message: "Sem conexão com a API.", authRequired: false }; });

    const { result } = renderHook(() => useSummary("2026-11", 0, noop));

    await waitFor(() => expect(result.current.state).toEqual({ kind: "error", message: "Sem conexão com a API." }));
  });
});
