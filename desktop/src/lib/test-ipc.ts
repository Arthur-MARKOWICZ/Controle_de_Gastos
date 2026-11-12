/**
 * Backend IPC falso para os testes. Guarda o que foi chamado e devolve o que o
 * teste enfileirar — nenhum teste toca o runtime do Tauri nem a rede.
 */

import { setIpcBackend } from "./ipc";

export type IpcCall = { command: string; args: Record<string, unknown> };

type Handler = (args: Record<string, unknown>) => unknown;

export class FakeIpc {
  readonly calls: IpcCall[] = [];
  private readonly handlers = new Map<string, Handler>();

  on(command: string, handler: Handler): this {
    this.handlers.set(command, handler);
    return this;
  }

  /** Resposta de `api` para um caminho exato (ou prefixo). */
  onApi(path: string, reply: { status: number; body: unknown }): this {
    const existing = this.apiReplies.get(path) ?? [];
    existing.push(reply);
    this.apiReplies.set(path, existing);
    return this;
  }

  private readonly apiReplies = new Map<string, { status: number; body: unknown }[]>();

  install(): this {
    setIpcBackend(async (command, args = {}) => {
      this.calls.push({ command, args });
      const handler = this.handlers.get(command);
      if (handler) {
        return handler(args);
      }
      if (command === "api") {
        return this.replyForApi(args);
      }
      throw { message: `comando não esperado no teste: ${command}`, authRequired: false };
    });
    return this;
  }

  private replyForApi(args: Record<string, unknown>) {
    const path = String(args.path ?? "");
    const queue = this.apiReplies.get(path);
    const reply = queue && queue.length > 0
      ? (queue.length === 1 ? queue[0] : queue.shift()!)
      : undefined;
    if (!reply) {
      throw {
        message: `rota não enfileirada no teste: ${path}`,
        authRequired: false,
      };
    }
    return {
      status: reply.status,
      body: typeof reply.body === "string" ? reply.body : JSON.stringify(reply.body),
    };
  }

  apiCalls(): IpcCall[] {
    return this.calls.filter((call) => call.command === "api");
  }

  pathsCalled(): string[] {
    return this.apiCalls().map((call) => String(call.args.path ?? ""));
  }

  countPath(path: string): number {
    return this.pathsCalled().filter((p) => p === path).length;
  }
}

export function installFakeIpc(): FakeIpc {
  return new FakeIpc().install();
}

export function resetIpc(): void {
  setIpcBackend(null);
}
