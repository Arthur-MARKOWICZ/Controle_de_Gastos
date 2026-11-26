import { afterEach, describe, expect, it } from "vitest";
import {
  ApiError,
  dismissAlert,
  getLedgerSummary,
  hasExpenseToday,
  listAlerts,
  registerExpense,
} from "./api";
import { IpcError } from "./ipc";
import { summary } from "./fixtures";
import { installFakeIpc, resetIpc } from "./test-ipc";

afterEach(resetIpc);

describe("api do desktop", () => {
  it("busca o resumo do mês pela rota de ledger com o mês na query", async () => {
    const ipc = installFakeIpc().onApi("/api/v1/ledger/summary?month=2026-11", { status: 200, body: summary() });

    const result = await getLedgerSummary("2026-11");

    expect(result.unallocated.amount).toBe("1550.00");
    expect(ipc.apiCalls()[0].args).toMatchObject({ method: "GET", body: null });
  });

  it("registra gasto com valor em string decimal de duas casas", async () => {
    const ipc = installFakeIpc().onApi("/api/v1/envelopes/env-1/entries", { status: 201, body: { id: "x" } });

    await registerExpense("env-1", { amount: "10.50", occurredAt: "2026-11-05", description: null });

    expect(JSON.parse(String(ipc.apiCalls()[0].args.body))).toEqual({
      kind: "EXPENSE",
      amount: { amount: "10.50", currency: "BRL" },
      occurredAt: "2026-11-05",
      description: null,
    });
  });

  it("id de verba com barra é codificado e não muda a rota", async () => {
    const ipc = installFakeIpc().onApi("/api/v1/envelopes/a%2F..%2Fadmin/entries", { status: 201, body: {} });

    await registerExpense("a/../admin", { amount: "1.00", occurredAt: "2026-11-05", description: null });

    expect(ipc.pathsCalled()).toEqual(["/api/v1/envelopes/a%2F..%2Fadmin/entries"]);
  });

  it("resposta 204 sem corpo não é lida como JSON", async () => {
    installFakeIpc().onApi("/api/v1/alerts/alerta-1/dismiss", { status: 204, body: "" });

    await expect(dismissAlert("alerta-1")).resolves.toBeNull();
  });

  it("hasExpenseToday devolve só o booleano, sem a data", async () => {
    installFakeIpc().onApi("/api/v1/notifications/today", { status: 200, body: { date: "2026-11-26", hasExpense: true } });

    await expect(hasExpenseToday()).resolves.toBe(true);
  });

  it("403 sem corpo vira mensagem de acesso negado", async () => {
    installFakeIpc().onApi("/api/v1/alerts", { status: 403, body: "" });

    const error = await listAlerts().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(403);
    expect((error as ApiError).message).toBe("Você não tem acesso a esta verba.");
  });

  it("problem com detail usa o texto da API", async () => {
    installFakeIpc().onApi("/api/v1/envelopes/env-1/entries", {
      status: 422,
      body: { code: "INVALID_REQUEST", detail: "A data não pode ser futura." },
    });

    const error = await registerExpense("env-1", { amount: "1.00", occurredAt: "2099-01-01", description: null })
      .catch((e: unknown) => e);

    expect((error as ApiError).message).toBe("A data não pode ser futura.");
  });

  it("falha do servidor, 404 e status desconhecido têm mensagens próprias", async () => {
    installFakeIpc()
      .onApi("/api/v1/alerts", { status: 500, body: "<html>erro</html>" })
      .onApi("/api/v1/alerts", { status: 404, body: "" })
      .onApi("/api/v1/alerts", { status: 409, body: "{}" });

    const messages = [];
    for (let i = 0; i < 3; i++) messages.push(await listAlerts().catch((e: Error) => e.message));

    expect(messages).toEqual([
      "A API falhou. Tente de novo em instantes.",
      "Recurso não encontrado.",
      "A API recusou a operação (HTTP 409).",
    ]);
  });

  it("sessão perdida no processo nativo chega como IpcError com authRequired", async () => {
    installFakeIpc().on("api", () => {
      throw { message: "Sessão expirada.", authRequired: true };
    });

    const error = await listAlerts().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(IpcError);
    expect((error as IpcError).authRequired).toBe(true);
  });
});
