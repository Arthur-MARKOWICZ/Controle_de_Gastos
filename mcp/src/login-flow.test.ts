import { describe, expect, it, vi } from "vitest";
import { ApiError, type ControleGastosApiClient } from "./api-client.js";
import { runInteractiveLogin } from "./login-flow.js";

function fakeClient(
  overrides: Partial<ControleGastosApiClient> = {},
): ControleGastosApiClient {
  return {
    login: vi.fn(),
    verifyMfa: vi.fn(),
    ...overrides,
  } as unknown as ControleGastosApiClient;
}

describe("runInteractiveLogin", () => {
  it("autentica sem MFA e não imprime senha", async () => {
    const client = fakeClient({
      login: vi.fn().mockResolvedValue({ authenticated: true, expiresIn: 900 }),
    });
    const lines: string[] = [];
    const ask = vi
      .fn()
      .mockResolvedValueOnce("user@example.com")
      .mockResolvedValueOnce("never-print-this-password");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: (line) => lines.push(line),
    });

    expect(result).toEqual({ ok: true, expiresIn: 900 });
    expect(client.login).toHaveBeenCalledWith(
      "user@example.com",
      "never-print-this-password",
    );
    expect(lines.join("\n")).toMatch(/sessão salva/i);
    expect(lines.join("\n")).not.toContain("never-print-this-password");
  });

  it("pede código TOTP quando a API exige MFA", async () => {
    const client = fakeClient({
      login: vi.fn().mockResolvedValue({
        mfaRequired: true,
        challengeId: "chal-9",
        expiresIn: 300,
      }),
      verifyMfa: vi.fn().mockResolvedValue({ authenticated: true, expiresIn: 900 }),
    });
    const ask = vi
      .fn()
      .mockResolvedValueOnce("user@example.com")
      .mockResolvedValueOnce("secret-password")
      .mockResolvedValueOnce("123456");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: () => undefined,
    });

    expect(result).toEqual({ ok: true, expiresIn: 900 });
    expect(client.verifyMfa).toHaveBeenCalledWith("chal-9", "123456");
  });

  it("rejeita e-mail vazio sem chamar a API", async () => {
    const client = fakeClient({
      login: vi.fn(),
    });
    const ask = vi.fn().mockResolvedValueOnce("   ");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: () => undefined,
    });

    expect(result.ok).toBe(false);
    expect(client.login).not.toHaveBeenCalled();
  });

  it("em erro de API reporta status sem ecoar a senha", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new ApiError(401, '{"code":"BAD_CREDENTIALS"}')),
    });
    const lines: string[] = [];
    const ask = vi
      .fn()
      .mockResolvedValueOnce("user@example.com")
      .mockResolvedValueOnce("wrong-password");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: (line) => lines.push(line),
    });

    expect(result.ok).toBe(false);
    expect(lines.join("\n")).toMatch(/401/);
    expect(lines.join("\n")).not.toContain("wrong-password");
  });
});
