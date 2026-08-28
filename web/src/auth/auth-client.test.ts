import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthClient } from "./auth-client";

afterEach(() => vi.unstubAllGlobals());

describe("AuthClient", () => {
  it("restaura a sessão pelo cookie sem expor o refresh ao JavaScript", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: "access-1", tokenType: "Bearer", expiresIn: 900 }))
      .mockResolvedValueOnce(response({ id: "user-1", email: "pessoa@example.com", emailVerified: false,
        createdAt: "2026-08-27T00:00:00Z", updatedAt: "2026-08-27T00:00:00Z" }));
    vi.stubGlobal("fetch", fetchMock);

    const user = await new AuthClient("http://api.test").restore();

    expect(user?.email).toBe("pessoa@example.com");
    expect(fetchMock).toHaveBeenNthCalledWith(1, "http://api.test/api/v1/auth/refresh",
      expect.objectContaining({ method: "POST", credentials: "include" }));
    expect(fetchMock.mock.calls[1][0]).toBe("http://api.test/api/v1/users/me");
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get("Authorization")).toBe("Bearer access-1");
  });

  it("consolida renovações simultâneas em uma única chamada", async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({
      accessToken: "access-2", tokenType: "Bearer", expiresIn: 900,
    }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new AuthClient("http://api.test");

    await Promise.all([client.refresh(), client.refresh(), client.refresh()]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("fica anônimo quando a API está offline durante a restauração", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));

    await expect(new AuthClient("http://api.test").restore()).resolves.toBeNull();
  });
});

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
