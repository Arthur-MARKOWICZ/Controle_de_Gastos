import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { runLoginCli } from "./login-cli.js";

const PASSWORD = "senha-que-nunca-aparece";
const ACCESS = "jwt.access.secreto";
const REFRESH = "refresh-opaco-secreto";

let dir: string;
let sessionPath: string;
let printed: string;

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "cg-login-cli-"));
  sessionPath = join(dir, "sessao.json");
  printed = "";
});

afterEach(() => rmSync(dir, { recursive: true, force: true }));

function env() {
  return { CONTROLE_GASTOS_API_BASE_URL: "http://api.test", CONTROLE_GASTOS_SESSION_PATH: sessionPath };
}

function answers(...values: string[]) {
  const ask = vi.fn();
  for (const value of values) ask.mockResolvedValueOnce(value);
  return ask;
}

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });
}

const authenticated = () =>
  json(200, { accessToken: ACCESS, expiresIn: 900 }, { "Set-Cookie": `refresh_token=${REFRESH}; Path=/api/v1/auth; HttpOnly` });

function run(ask: ReturnType<typeof vi.fn>, fetchImpl: typeof fetch) {
  return runLoginCli({ env: env(), ask, fetchImpl, write: (text) => { printed += text; } });
}

describe("login-cli", () => {
  it("login feliz salva a sessão em arquivo e sai com 0", async () => {
    const code = await run(answers("ana@example.com", PASSWORD), vi.fn().mockResolvedValue(authenticated()));

    expect(code).toBe(0);
    expect(JSON.parse(readFileSync(sessionPath, "utf8"))).toMatchObject({ accessToken: ACCESS, refreshToken: REFRESH });
    expect(printed).toMatch(/Sessão salva/);
  });

  it("com MFA pede o código e só então salva a sessão", async () => {
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(json(200, { mfaRequired: true, challengeId: "desafio-1", expiresIn: 300 }))
      .mockResolvedValueOnce(authenticated());

    const code = await run(answers("ana@example.com", PASSWORD, "123456"), fetchImpl);

    expect(code).toBe(0);
    expect(JSON.parse(String(fetchImpl.mock.calls[1][1].body))).toEqual({ challengeId: "desafio-1", code: "123456" });
    expect(existsSync(sessionPath)).toBe(true);
  });

  it("senha errada não cria sessão e sai com 1", async () => {
    const code = await run(
      answers("ana@example.com", "senha errada"),
      vi.fn().mockResolvedValue(json(401, { code: "AUTHENTICATION_FAILED" })),
    );

    expect(code).toBe(1);
    expect(printed).toContain("Falha no login (HTTP 401). Verifique e-mail/senha ou MFA.");
    expect(existsSync(sessionPath)).toBe(false);
  });

  it("API indisponível informa a falha e não cria sessão", async () => {
    const code = await run(answers("ana@example.com", PASSWORD), vi.fn().mockRejectedValue(new TypeError("fetch failed")));

    expect(code).toBe(1);
    expect(printed).toContain("Falha no login: fetch failed");
    expect(existsSync(sessionPath)).toBe(false);
  });

  it("sessão salva corrompida é ignorada e substituída pelo login novo", async () => {
    writeFileSync(sessionPath, "{ isto não é json");

    const code = await run(answers("ana@example.com", PASSWORD), vi.fn().mockResolvedValue(authenticated()));

    expect(code).toBe(0);
    expect(JSON.parse(readFileSync(sessionPath, "utf8")).refreshToken).toBe(REFRESH);
  });

  it("nunca imprime senha nem tokens", async () => {
    await run(answers("ana@example.com", PASSWORD), vi.fn().mockResolvedValue(authenticated()));

    expect(printed).not.toContain(PASSWORD);
    expect(printed).not.toContain(ACCESS);
    expect(printed).not.toContain(REFRESH);
  });

  it("e-mail vazio encerra sem chamar a API", async () => {
    const fetchImpl = vi.fn();

    const code = await run(answers("   "), fetchImpl);

    expect(code).toBe(1);
    expect(printed).toContain("E-mail obrigatório.");
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("sem a URL da API falha antes de pedir credenciais", async () => {
    const ask = vi.fn();

    await expect(runLoginCli({ env: {}, ask, write: () => undefined })).rejects.toThrow(
      "CONTROLE_GASTOS_API_BASE_URL is required",
    );
    expect(ask).not.toHaveBeenCalled();
  });
});
