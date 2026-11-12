import { afterEach, describe, expect, it } from "vitest";
import { IpcError, restore, setIpcBackend } from "./ipc";

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
});
