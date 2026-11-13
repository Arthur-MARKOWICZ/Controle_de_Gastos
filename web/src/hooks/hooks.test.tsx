import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useEnvelopes } from "./useEnvelopes";
import { useIncome, useIncomeHistory } from "./useIncome";
import { useLedgerSummary } from "./useLedgerSummary";

const auth = vi.hoisted(() => {
  const request = vi.fn();
  // O cliente precisa ser estável entre renders, como no AuthProvider real.
  return { request, client: { request }, state: "authenticated" as string };
});

vi.mock("../auth/auth-context", () => ({
  useAuth: () => ({ client: auth.client, state: auth.state }),
}));

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const summary = { income: null, allocated: { amount: "0.00", currency: "BRL" }, unallocated: { amount: "0.00", currency: "BRL" }, usagePct: 0, envelopes: [] };
const envelope = { id: "e1", name: "Mercado" };
const income = { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-09", changedAt: "2026-09-01T00:00:00Z" };

beforeEach(() => {
  auth.request.mockReset();
  auth.state = "authenticated";
});

describe("useEnvelopes", () => {
  it("carrega as verbas do mês pedido", async () => {
    auth.request.mockResolvedValueOnce(json(200, [envelope]));

    const { result } = renderHook(() => useEnvelopes("2026-09"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.envelopes).toEqual([envelope]);
    expect(auth.request).toHaveBeenCalledWith("/api/v1/envelopes?month=2026-09");
  });

  it("sem sessão não chama a API e encerra o carregamento", async () => {
    auth.state = "anonymous";

    const { result } = renderHook(() => useEnvelopes());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(auth.request).not.toHaveBeenCalled();
  });

  it("refetch depois de carregar marca busy e mantém os dados antigos visíveis", async () => {
    auth.request.mockResolvedValueOnce(json(200, [envelope]));
    let release: (r: Response) => void = () => {};
    auth.request.mockReturnValueOnce(new Promise<Response>((resolve) => { release = resolve; }));
    const { result } = renderHook(() => useEnvelopes());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => { void result.current.refresh(); });

    await waitFor(() => expect(result.current.busy).toBe(true));
    expect(result.current.envelopes).toEqual([envelope]);
    await act(async () => release(json(200, [])));
    expect(result.current.busy).toBe(false);
  });

  it("erro 500 mostra a mensagem da API e deixa a lista vazia", async () => {
    auth.request.mockResolvedValueOnce(json(500, { code: "INTERNAL_ERROR", detail: "Não foi possível concluir a operação.", title: "Erro" }));

    const { result } = renderHook(() => useEnvelopes());

    await waitFor(() => expect(result.current.error).toBe("Não foi possível concluir a operação."));
    expect(result.current.envelopes).toEqual([]);
  });

  it("falha de rede mostra a mensagem do erro", async () => {
    auth.request.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const { result } = renderHook(() => useEnvelopes());

    await waitFor(() => expect(result.current.error).toBe("Failed to fetch"));
  });

  it("rejeição sem Error usa a mensagem padrão em português", async () => {
    auth.request.mockRejectedValueOnce("boom");

    const { result } = renderHook(() => useEnvelopes());

    await waitFor(() => expect(result.current.error).toBe("Não foi possível carregar as verbas"));
  });

  it("refetch bem-sucedido limpa o erro anterior", async () => {
    auth.request.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    auth.request.mockResolvedValueOnce(json(200, [envelope]));
    const { result } = renderHook(() => useEnvelopes());
    await waitFor(() => expect(result.current.error).not.toBeNull());

    await act(async () => { await result.current.refresh(); });

    expect(result.current.error).toBeNull();
    expect(result.current.envelopes).toEqual([envelope]);
  });
});

describe("useIncome", () => {
  it("carrega a renda configurada", async () => {
    auth.request.mockResolvedValueOnce(json(200, income));

    const { result } = renderHook(() => useIncome("2026-09"));

    await waitFor(() => expect(result.current.income).toEqual(income));
    expect(result.current.notConfigured).toBe(false);
  });

  it("404 INCOME_NOT_CONFIGURED vira estado 'não configurada', sem erro", async () => {
    auth.request.mockResolvedValueOnce(json(404, { code: "INCOME_NOT_CONFIGURED", detail: "Renda não configurada", title: "x" }));

    const { result } = renderHook(() => useIncome());

    await waitFor(() => expect(result.current.notConfigured).toBe(true));
    expect(result.current.error).toBeNull();
  });

  it("404 com outro código é tratado como erro", async () => {
    auth.request.mockResolvedValueOnce(json(404, { code: "NOT_FOUND", detail: "Recurso não encontrado", title: "x" }));

    const { result } = renderHook(() => useIncome());

    await waitFor(() => expect(result.current.error).toBe("Recurso não encontrado"));
    expect(result.current.notConfigured).toBe(false);
  });

  it("401 mostra o detalhe de autenticação", async () => {
    auth.request.mockResolvedValueOnce(json(401, { code: "AUTHENTICATION_FAILED", detail: "Sessão expirada", title: "x" }));

    const { result } = renderHook(() => useIncome());

    await waitFor(() => expect(result.current.error).toBe("Sessão expirada"));
  });

  it("histórico de renda pagina com o tamanho pedido", async () => {
    auth.request.mockResolvedValueOnce(json(200, { items: [], page: 2, size: 5, hasNext: false }));

    const { result } = renderHook(() => useIncomeHistory(2, 5));

    await waitFor(() => expect(result.current.data?.page).toBe(2));
    expect(auth.request).toHaveBeenCalledWith("/api/v1/income/history?page=2&size=5");
  });

  it("falha no histórico mostra mensagem padrão quando o erro não tem texto", async () => {
    auth.request.mockRejectedValueOnce({});

    const { result } = renderHook(() => useIncomeHistory());

    await waitFor(() => expect(result.current.error).toBe("Não foi possível carregar o histórico"));
  });

  it("histórico sem sessão não chama a API", async () => {
    auth.state = "anonymous";

    const { result } = renderHook(() => useIncomeHistory());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(auth.request).not.toHaveBeenCalled();
  });
});

describe("useLedgerSummary", () => {
  it("carrega o resumo do mês", async () => {
    auth.request.mockResolvedValueOnce(json(200, summary));

    const { result } = renderHook(() => useLedgerSummary("2026-09"));

    await waitFor(() => expect(result.current.summary).toEqual(summary));
    expect(auth.request).toHaveBeenCalledWith("/api/v1/ledger/summary?month=2026-09");
  });

  it("troca de mês recarrega do zero, sem misturar dados do mês anterior", async () => {
    auth.request.mockResolvedValueOnce(json(200, summary));
    auth.request.mockReturnValueOnce(new Promise<Response>(() => {}));
    const { result, rerender } = renderHook(({ month }) => useLedgerSummary(month), { initialProps: { month: "2026-09" } });
    await waitFor(() => expect(result.current.summary).toEqual(summary));

    rerender({ month: "2026-10" });

    await waitFor(() => expect(result.current.loading).toBe(true));
    expect(result.current.summary).toBeNull();
  });

  it("403 mostra o detalhe recebido", async () => {
    auth.request.mockResolvedValueOnce(json(403, { code: "RESTRICTED_SESSION", detail: "Sessão restrita", title: "x" }));

    const { result } = renderHook(() => useLedgerSummary());

    await waitFor(() => expect(result.current.error).toBe("Sessão restrita"));
  });

  it("resposta sem corpo JSON vira mensagem com o status", async () => {
    auth.request.mockResolvedValueOnce(new Response("gateway", { status: 504 }));

    const { result } = renderHook(() => useLedgerSummary());

    await waitFor(() => expect(result.current.error).toBe("Erro 504"));
  });

  it("falha de rede mantém o resumo nulo", async () => {
    auth.request.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const { result } = renderHook(() => useLedgerSummary());

    await waitFor(() => expect(result.current.error).toBe("Failed to fetch"));
    expect(result.current.summary).toBeNull();
  });

  it("sem sessão não busca o resumo", async () => {
    auth.state = "anonymous";

    const { result } = renderHook(() => useLedgerSummary());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(auth.request).not.toHaveBeenCalled();
  });

  it("rejeição sem Error usa mensagem padrão do resumo", async () => {
    auth.request.mockRejectedValueOnce(42);

    const { result } = renderHook(() => useLedgerSummary());

    await waitFor(() => expect(result.current.error).toBe("Não foi possível carregar o resumo"));
  });
});
