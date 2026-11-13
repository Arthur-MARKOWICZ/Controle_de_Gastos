/**
 * Ponte com o processo nativo (ADR-0029).
 *
 * A interface nunca fala HTTP direto: toda chamada à API passa por um comando
 * Tauri, e o access/refresh ficam no processo Rust. O webview só vê e-mail e
 * DTOs.
 *
 * `setIpcBackend` existe para o teste injetar um backend falso — sem ele, o
 * jsdom tentaria carregar o runtime do Tauri, que não existe fora da janela.
 */

export type IpcBackend = (
  command: string,
  args?: Record<string, unknown>,
) => Promise<unknown>;

let backend: IpcBackend | null = null;

export function setIpcBackend(next: IpcBackend | null): void {
  backend = next;
}

async function call<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  if (backend) {
    return (await backend(command, args)) as T;
  }
  // O Vite em localhost:1420 também abre num navegador comum, onde não há
  // processo nativo: sem isto o erro seria um "reading 'invoke'" críptico.
  if (!("__TAURI_INTERNALS__" in window)) {
    throw new IpcError(
      "Este endereço é só o servidor de desenvolvimento. Abra o aplicativo pela " +
        "janela do Tauri (pnpm tauri dev em desktop/), não pelo navegador.",
    );
  }
  const { invoke } = await import("@tauri-apps/api/core");
  return invoke<T>(command, args);
}

/** Erro vindo do processo nativo, já com mensagem pronta para leitura. */
export class IpcError extends Error {
  readonly authRequired: boolean;

  constructor(message: string, authRequired = false) {
    super(message);
    this.name = "IpcError";
    this.authRequired = authRequired;
  }
}

function toIpcError(raw: unknown): IpcError {
  if (raw && typeof raw === "object" && "message" in raw) {
    const view = raw as { message?: unknown; authRequired?: unknown };
    return new IpcError(
      typeof view.message === "string" ? view.message : "Falha inesperada no cliente.",
      view.authRequired === true,
    );
  }
  if (raw instanceof Error) {
    return new IpcError(raw.message);
  }
  return new IpcError("Falha inesperada no cliente.");
}

async function guarded<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  try {
    return await call<T>(command, args);
  } catch (raw) {
    throw toIpcError(raw);
  }
}

export type SessionView = { email: string };

export type AuthReply =
  | { status: "authenticated"; session: SessionView }
  | { status: "mfaRequired"; challengeId: string; expiresIn: number };

export type RestoreReply = { session: SessionView | null };

export type ApiReply = { status: number; body: string };

export type ThemeMode = "SYSTEM" | "LIGHT" | "DARK";

export function login(email: string, password: string): Promise<AuthReply> {
  return guarded<AuthReply>("login", { email, password });
}

export function verifyMfa(challengeId: string, code: string): Promise<AuthReply> {
  return guarded<AuthReply>("verify_mfa", { challengeId, code });
}

export function restore(): Promise<RestoreReply> {
  return guarded<RestoreReply>("restore");
}

export function logout(): Promise<void> {
  return guarded<void>("logout");
}

/** Chamada autenticada crua. Preferir os helpers de `api.ts`. */
export function apiCall(
  method: "GET" | "POST" | "PATCH",
  path: string,
  body?: unknown,
): Promise<ApiReply> {
  return guarded<ApiReply>("api", {
    method,
    path,
    body: body === undefined ? null : JSON.stringify(body),
  });
}

export function themeGet(): Promise<ThemeMode> {
  return guarded<ThemeMode>("theme_get");
}

export function themeSet(mode: ThemeMode): Promise<void> {
  return guarded<void>("theme_set", { mode });
}
