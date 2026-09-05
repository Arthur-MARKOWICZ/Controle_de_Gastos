import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthClient, AuthError } from "./auth-client";

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

  it("envia a solicitação e a confirmação de recuperação sem token de sessão", async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({}));
    vi.stubGlobal("fetch", fetchMock);
    const client = new AuthClient("http://api.test");

    await client.requestPasswordReset("pessoa@example.com");
    await client.resetPassword("token-seguro", "uma senha nova segura");

    expect(fetchMock).toHaveBeenNthCalledWith(1, "http://api.test/api/v1/auth/password-reset-requests",
      expect.objectContaining({ method: "POST", credentials: "include", body: JSON.stringify({ email: "pessoa@example.com" }) }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, "http://api.test/api/v1/auth/password-resets",
      expect.objectContaining({ method: "POST", credentials: "include", body: JSON.stringify({ token: "token-seguro", newPassword: "uma senha nova segura" }) }));
  });

  it("lança AuthError mfa-required com o challengeId quando o login exige segundo fator", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      response({ mfaRequired: true, challengeId: "desafio-1", expiresIn: 300 }),
    ));
    const client = new AuthClient("http://api.test");

    const error = await client.login("pessoa@example.com", "senha").catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(AuthError);
    expect((error as AuthError).kind).toBe("mfa-required");
    expect((error as AuthError).challengeId).toBe("desafio-1");
  });

  it("verifica o código MFA e restaura a sessão normalmente", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: "access-3", tokenType: "Bearer", expiresIn: 900 }))
      .mockResolvedValueOnce(response({ id: "user-1", email: "pessoa@example.com", emailVerified: false,
        createdAt: "2026-08-27T00:00:00Z", updatedAt: "2026-08-27T00:00:00Z" }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new AuthClient("http://api.test");

    const user = await client.verifyMfa("desafio-1", "123456");

    expect(user.email).toBe("pessoa@example.com");
    expect(fetchMock).toHaveBeenNthCalledWith(1, "http://api.test/api/v1/auth/mfa/verify",
      expect.objectContaining({ method: "POST", credentials: "include", body: JSON.stringify({ challengeId: "desafio-1", code: "123456" }) }));
  });

  it("consome um recovery code e devolve apenas o token restrito", async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ restrictedToken: "token-restrito", tokenType: "Bearer", expiresIn: 600 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new AuthClient("http://api.test");

    const restrictedToken = await client.verifyRecoveryCode("desafio-1", "ABCDE-FGHJK");

    expect(restrictedToken).toBe("token-restrito");
    expect(fetchMock).toHaveBeenCalledWith("http://api.test/api/v1/auth/mfa/recovery",
      expect.objectContaining({ method: "POST", credentials: "include", body: JSON.stringify({ challengeId: "desafio-1", recoveryCode: "ABCDE-FGHJK" }) }));
  });

  it("usa o token restrito diretamente no enroll, sem depender do access token nem de retry em 401", async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({}, 403));
    vi.stubGlobal("fetch", fetchMock);
    const client = new AuthClient("http://api.test");

    await expect(client.startMfaEnrollment("senha", "token-restrito")).rejects.toBeInstanceOf(AuthError);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, init] = fetchMock.mock.calls[0];
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer token-restrito");
  });
});

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
