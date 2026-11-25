import { afterEach, describe, expect, it, vi } from "vitest";

const push = vi.hoisted(() => ({
  currentEndpoint: vi.fn(),
  unsubscribeThisBrowser: vi.fn(),
}));
vi.mock("../lib/web-push", () => push);

import { AuthClient } from "./auth-client";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

async function signedIn(fetchMock: ReturnType<typeof vi.fn>) {
  fetchMock
    .mockResolvedValueOnce(json({ accessToken: "access-1", tokenType: "Bearer", expiresIn: 900 }))
    .mockResolvedValueOnce(json({ id: "u", email: "p@example.com", emailVerified: true, createdAt: "", updatedAt: "" }));
  vi.stubGlobal("fetch", fetchMock);
  const client = new AuthClient("http://api.test");
  await client.restore();
  return client;
}

describe("AuthClient.logout e Web Push", () => {
  it("remove a assinatura deste navegador na API e no navegador antes de encerrar a sessão", async () => {
    const fetchMock = vi.fn();
    const client = await signedIn(fetchMock);
    push.currentEndpoint.mockResolvedValue("https://push.example.com/este");
    push.unsubscribeThisBrowser.mockResolvedValue("https://push.example.com/este");
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await client.logout();

    const urls = fetchMock.mock.calls.slice(2).map((call) => call[0]);
    expect(urls).toEqual([
      "http://api.test/api/v1/notifications/web-push/subscriptions/remove",
      "http://api.test/api/v1/auth/logout",
    ]);
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ endpoint: "https://push.example.com/este" });
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get("Authorization")).toBe("Bearer access-1");
    expect(push.unsubscribeThisBrowser).toHaveBeenCalled();
  });

  it("sem assinatura neste navegador, só encerra a sessão", async () => {
    const fetchMock = vi.fn();
    const client = await signedIn(fetchMock);
    push.currentEndpoint.mockResolvedValue(null);
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await client.logout();

    expect(fetchMock.mock.calls.slice(2).map((call) => call[0])).toEqual(["http://api.test/api/v1/auth/logout"]);
    expect(push.unsubscribeThisBrowser).not.toHaveBeenCalled();
  });

  it("falha ao remover a assinatura não impede o logout", async () => {
    const fetchMock = vi.fn();
    const client = await signedIn(fetchMock);
    push.currentEndpoint.mockRejectedValue(new Error("sem service worker"));
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await client.logout();

    expect(fetchMock.mock.calls.slice(2).map((call) => call[0])).toEqual(["http://api.test/api/v1/auth/logout"]);
  });
});
