import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";

type Listener = (event: Record<string, unknown>) => void;

/** Executa public/sw.js com um `self` falso e devolve os listeners registrados. */
function loadWorker() {
  const listeners: Record<string, Listener> = {};
  const showNotification = vi.fn().mockResolvedValue(undefined);
  const openWindow = vi.fn().mockResolvedValue(undefined);
  const windows: Array<{ url: string; focus: ReturnType<typeof vi.fn>; navigate: ReturnType<typeof vi.fn> }> = [];
  const self = {
    addEventListener: (type: string, listener: Listener) => { listeners[type] = listener; },
    registration: { showNotification },
    location: { origin: "https://gastos.example.com" },
    clients: {
      matchAll: vi.fn().mockImplementation(async () => windows),
      openWindow,
      claim: vi.fn().mockResolvedValue(undefined),
    },
    skipWaiting: vi.fn().mockResolvedValue(undefined),
  };
  const code = readFileSync(join(process.cwd(), "public", "sw.js"), "utf8");
  new Function("self", code)(self);
  return { listeners, showNotification, openWindow, windows };
}

async function dispatch(listener: Listener, event: Record<string, unknown>) {
  let pending: Promise<unknown> = Promise.resolve();
  listener({ ...event, waitUntil: (promise: Promise<unknown>) => { pending = promise; } });
  await pending;
}

describe("service worker", () => {
  it("não intercepta requisições: sem cache de dados", () => {
    expect(loadWorker().listeners.fetch).toBeUndefined();
  });

  it.each([
    [{ category: "NEGATIVE_BALANCE", purpose: "LIMIT", path: "/" }, "Você tem um alerta de verba do tipo Limite"],
    [{ category: "SHARED_EXPENSE", purpose: "GOAL", path: "/" }, "Novo gasto em verba compartilhada do tipo Meta"],
    [{ category: "NEGATIVE_BALANCE", purpose: "SAVINGS_TARGET", path: "/" }, "Você tem um alerta de verba do tipo Meta de poupança"],
    [{ category: "DAILY_REMINDER", path: "/" }, "Nenhum lançamento hoje"],
  ])("monta o texto localmente a partir de %o", async (payload, body) => {
    const { listeners, showNotification } = loadWorker();

    await dispatch(listeners.push, { data: { json: () => payload } });

    expect(showNotification).toHaveBeenCalledWith("Controle de gastos", expect.objectContaining({ body }));
  });

  it("usa um texto genérico para categoria desconhecida ou payload inválido", async () => {
    const { listeners, showNotification } = loadWorker();

    await dispatch(listeners.push, { data: { json: () => { throw new Error("inválido"); } } });

    expect(showNotification).toHaveBeenCalledWith("Controle de gastos", expect.objectContaining({ body: "Você tem um novo aviso" }));
  });

  it("ao clicar, foca uma janela aberta e navega para o caminho do aviso", async () => {
    const { listeners, windows, openWindow } = loadWorker();
    const focus = vi.fn().mockResolvedValue(undefined);
    const navigate = vi.fn().mockResolvedValue(undefined);
    windows.push({ url: "https://gastos.example.com/metas", focus, navigate });
    const close = vi.fn();

    await dispatch(listeners.notificationclick, { notification: { close, data: { path: "/gastos" } } });

    expect(close).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith("/gastos");
    expect(focus).toHaveBeenCalled();
    expect(openWindow).not.toHaveBeenCalled();
  });

  it("sem janela aberta, abre o app; caminho externo vira a raiz", async () => {
    const { listeners, openWindow } = loadWorker();

    await dispatch(listeners.notificationclick, { notification: { close: vi.fn(), data: { path: "//evil.example.com" } } });

    expect(openWindow).toHaveBeenCalledWith("/");
  });

  it("push sem dados usa o texto genérico e a etiqueta padrão", async () => {
    const { listeners, showNotification } = loadWorker();

    await dispatch(listeners.push, {});

    expect(showNotification).toHaveBeenCalledWith("Controle de gastos", expect.objectContaining({
      body: "Você tem um novo aviso",
      tag: "aviso",
      data: { path: "/" },
    }));
  });

  it("nunca mostra nome de verba ou valor que venham por engano no payload", async () => {
    const { listeners, showNotification } = loadWorker();

    await dispatch(listeners.push, { data: { json: () => ({
      category: "NEGATIVE_BALANCE", purpose: "LIMIT", path: "/", envelopeName: "Mercado", amount: "150.00",
    }) } });

    const options = showNotification.mock.calls[0][1] as { body: string };
    expect(JSON.stringify(options)).not.toContain("Mercado");
    expect(JSON.stringify(options)).not.toContain("150.00");
  });

  it("caminho javascript: no aviso vira a raiz ao clicar", async () => {
    const { listeners, openWindow } = loadWorker();

    await dispatch(listeners.notificationclick, { notification: { close: vi.fn(), data: { path: "javascript:alert(1)" } } });

    expect(openWindow).toHaveBeenCalledWith("/");
  });

  it("janela de outra origem não é reaproveitada ao clicar", async () => {
    const { listeners, windows, openWindow } = loadWorker();
    const navigate = vi.fn().mockResolvedValue(undefined);
    windows.push({ url: "https://outro.example.com/", focus: vi.fn(), navigate });

    await dispatch(listeners.notificationclick, { notification: { close: vi.fn(), data: { path: "/metas" } } });

    expect(navigate).not.toHaveBeenCalled();
    expect(openWindow).toHaveBeenCalledWith("/metas");
  });

  it("falha ao navegar a janela aberta rejeita com o erro original sem abrir outra", async () => {
    const { listeners, windows, openWindow } = loadWorker();
    windows.push({
      url: "https://gastos.example.com/",
      focus: vi.fn(),
      navigate: vi.fn().mockRejectedValue(new TypeError("navegação bloqueada")),
    });

    await expect(dispatch(listeners.notificationclick, { notification: { close: vi.fn(), data: { path: "/" } } }))
      .rejects.toThrow("navegação bloqueada");
    expect(openWindow).not.toHaveBeenCalled();
  });

  it("falha ao exibir a notificação rejeita com o erro original", async () => {
    const { listeners, showNotification } = loadWorker();
    showNotification.mockRejectedValueOnce(new DOMException("Permissão negada", "NotAllowedError"));

    await expect(dispatch(listeners.push, { data: { json: () => ({ category: "DAILY_REMINDER", path: "/" }) } }))
      .rejects.toThrow("Permissão negada");
  });
});
