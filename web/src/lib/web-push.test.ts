import { afterEach, describe, expect, it, vi } from "vitest";
import { PushPermissionDenied, currentEndpoint, pushSupport, subscribeThisBrowser, unsubscribeThisBrowser } from "./web-push";

const originalNavigator = globalThis.navigator;

type FakeSubscription = { endpoint: string; toJSON(): unknown; unsubscribe: ReturnType<typeof vi.fn> };

function installBrowser({ subscription = null as FakeSubscription | null, permission = "granted" } = {}) {
  const pushManager = {
    getSubscription: vi.fn().mockResolvedValue(subscription),
    subscribe: vi.fn().mockImplementation(async () => ({
      endpoint: "https://push.example.com/novo",
      toJSON: () => ({ endpoint: "https://push.example.com/novo", keys: { p256dh: "P", auth: "A" } }),
      unsubscribe: vi.fn(),
    })),
  };
  const registration = { pushManager };
  const serviceWorker = {
    register: vi.fn().mockResolvedValue(registration),
    getRegistration: vi.fn().mockResolvedValue(registration),
  };
  Object.defineProperty(globalThis, "navigator", {
    configurable: true,
    value: { serviceWorker, userAgent: "Mozilla/5.0 (X11; Linux x86_64)", maxTouchPoints: 0 },
  });
  vi.stubGlobal("PushManager", function PushManager() {});
  vi.stubGlobal("Notification", { permission, requestPermission: vi.fn().mockResolvedValue(permission) });
  return { pushManager, serviceWorker };
}

afterEach(() => {
  vi.unstubAllGlobals();
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: originalNavigator });
});

describe("web-push", () => {
  it("detecta suporte quando há service worker, PushManager e Notification", () => {
    installBrowser();
    expect(pushSupport()).toBe("supported");
  });

  it("no iOS fora do app instalado, pede a instalação", () => {
    Object.defineProperty(globalThis, "navigator", {
      configurable: true,
      value: { userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", maxTouchPoints: 5 },
    });
    expect(pushSupport()).toBe("ios-needs-install");
  });

  it("sem as APIs e fora do iOS, não há suporte", () => {
    Object.defineProperty(globalThis, "navigator", {
      configurable: true,
      value: { userAgent: "Mozilla/5.0 (X11; Linux x86_64)", maxTouchPoints: 0 },
    });
    expect(pushSupport()).toBe("unsupported");
  });

  it("assina com a chave do servidor depois da permissão e devolve endpoint e chaves", async () => {
    const { pushManager, serviceWorker } = installBrowser();

    const subscription = await subscribeThisBrowser("AQAB");

    expect(serviceWorker.register).toHaveBeenCalledWith("/sw.js", { scope: "/" });
    expect(pushManager.subscribe).toHaveBeenCalledWith({
      userVisibleOnly: true,
      applicationServerKey: new Uint8Array([1, 0, 1]),
    });
    expect(subscription).toEqual({ endpoint: "https://push.example.com/novo", keys: { p256dh: "P", auth: "A" } });
  });

  it("com permissão negada, não assina", async () => {
    const { pushManager } = installBrowser({ permission: "denied" });

    await expect(subscribeThisBrowser("AQAB")).rejects.toBeInstanceOf(PushPermissionDenied);
    expect(pushManager.subscribe).not.toHaveBeenCalled();
  });

  it("cancela a assinatura atual e devolve o endpoint para a API remover", async () => {
    const existing = { endpoint: "https://push.example.com/atual", toJSON: () => ({}), unsubscribe: vi.fn().mockResolvedValue(true) };
    installBrowser({ subscription: existing });

    await expect(currentEndpoint()).resolves.toBe("https://push.example.com/atual");
    await expect(unsubscribeThisBrowser()).resolves.toBe("https://push.example.com/atual");
    expect(existing.unsubscribe).toHaveBeenCalled();
  });

  it("sem service worker, não há assinatura atual", async () => {
    Object.defineProperty(globalThis, "navigator", { configurable: true, value: { userAgent: "x" } });

    await expect(currentEndpoint()).resolves.toBeNull();
    await expect(unsubscribeThisBrowser()).resolves.toBeNull();
  });
});
