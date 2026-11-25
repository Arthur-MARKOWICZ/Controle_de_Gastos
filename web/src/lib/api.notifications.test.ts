import { describe, expect, it, vi } from "vitest";
import type { AuthClient } from "../auth/auth-client";
import { createApiClient } from "./api";

function clientReturning(response: Response) {
  const request = vi.fn().mockResolvedValue(response);
  return { api: createApiClient({ request } as unknown as AuthClient), request };
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("API de notificações", () => {
  it("lê as preferências", async () => {
    const prefs = { dailyReminder: false, negativeBalance: true, sharedExpense: false, reminderTime: "20:00" };
    const { api, request } = clientReturning(json(200, prefs));

    await expect(api.getNotificationPreferences()).resolves.toEqual(prefs);
    expect(request).toHaveBeenCalledWith("/api/v1/notifications/preferences");
  });

  it("salva as preferências com PUT", async () => {
    const prefs = { dailyReminder: true, negativeBalance: false, sharedExpense: true, reminderTime: "21:15" };
    const { api, request } = clientReturning(json(200, prefs));

    await api.updateNotificationPreferences(prefs);

    expect(request).toHaveBeenCalledWith("/api/v1/notifications/preferences", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(prefs),
    });
  });

  it("lê a chave pública VAPID, que pode ser nula", async () => {
    const { api } = clientReturning(json(200, { publicKey: null }));

    await expect(api.getWebPushPublicKey()).resolves.toBeNull();
  });

  it("registra a assinatura com endpoint e chaves", async () => {
    const { api, request } = clientReturning(new Response(null, { status: 204 }));
    const subscription = { endpoint: "https://push.example.com/x", keys: { p256dh: "p", auth: "a" } };

    await api.registerWebPushSubscription(subscription);

    expect(request).toHaveBeenCalledWith("/api/v1/notifications/web-push/subscriptions", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(subscription),
    });
  });

  it("remove a assinatura por POST, sem pôr o endpoint na URL", async () => {
    const { api, request } = clientReturning(new Response(null, { status: 204 }));

    await api.removeWebPushSubscription("https://push.example.com/x");

    expect(request).toHaveBeenCalledWith("/api/v1/notifications/web-push/subscriptions/remove", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ endpoint: "https://push.example.com/x" }),
    });
  });
});
