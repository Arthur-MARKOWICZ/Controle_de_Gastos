import { afterEach, describe, expect, it } from "vitest";
import { apiCall, IpcError, login, restore, setIpcBackend, themeSet } from "./ipc";

afterEach(() => setIpcBackend(null));

describe("ponte IPC fora da janela do Tauri", () => {
  it("aberto num navegador comum, explica que o app roda pela janela do Tauri", async () => {
    // jsdom não tem `__TAURI_INTERNALS__`, como o Vite aberto no navegador.
    setIpcBackend(null);

    const erro = await restore().catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(IpcError);
    expect((erro as IpcError).message).toMatch(/pnpm tauri dev/);
    expect((erro as IpcError).message).not.toMatch(/invoke/);
  });

  it("login repassa e-mail e senha ao comando nativo", async () => {
    const calls: unknown[] = [];
    setIpcBackend(async (command, args) => {
      calls.push([command, args]);
      return { status: "authenticated", session: { email: "ana@example.com" } };
    });

    await expect(login("ana@example.com", "frase segura")).resolves.toMatchObject({ status: "authenticated" });
    expect(calls).toEqual([["login", { email: "ana@example.com", password: "frase segura" }]]);
  });

  it("apiCall serializa o corpo e manda null quando não há corpo", async () => {
    const bodies: unknown[] = [];
    setIpcBackend(async (_command, args) => {
      bodies.push(args?.body);
      return { status: 200, body: "{}" };
    });

    await apiCall("POST", "/x", { valor: "10.50" });
    await apiCall("GET", "/x");

    expect(bodies).toEqual(['{"valor":"10.50"}', null]);
  });

  it("tema escolhido vai ao processo nativo como está", async () => {
    const calls: unknown[] = [];
    setIpcBackend(async (command, args) => { calls.push([command, args]); });

    await themeSet("DARK");

    expect(calls).toEqual([["theme_set", { mode: "DARK" }]]);
  });

  it("erro nativo com authRequired vira IpcError marcado", async () => {
    setIpcBackend(async () => { throw { message: "Sessão expirada.", authRequired: true }; });

    const erro = await restore().catch((e: unknown) => e);

    expect(erro).toBeInstanceOf(IpcError);
    expect((erro as IpcError).message).toBe("Sessão expirada.");
    expect((erro as IpcError).authRequired).toBe(true);
  });

  it("Error comum vira IpcError sem pedir login", async () => {
    setIpcBackend(async () => { throw new Error("cofre indisponível"); });

    const erro = (await restore().catch((e: unknown) => e)) as IpcError;

    expect(erro.message).toBe("cofre indisponível");
    expect(erro.authRequired).toBe(false);
  });

  it("falha sem mensagem legível usa o texto genérico", async () => {
    setIpcBackend(async () => { throw "panic"; });
    const deTexto = (await restore().catch((e: unknown) => e)) as IpcError;
    setIpcBackend(async () => { throw { message: 42 }; });
    const semTexto = (await restore().catch((e: unknown) => e)) as IpcError;

    expect(deTexto.message).toBe("Falha inesperada no cliente.");
    expect(semTexto.message).toBe("Falha inesperada no cliente.");
  });

  it("authRequired só vale quando é exatamente true", async () => {
    setIpcBackend(async () => { throw { message: "x", authRequired: "true" }; });

    const erro = (await restore().catch((e: unknown) => e)) as IpcError;

    expect(erro.authRequired).toBe(false);
  });
});
