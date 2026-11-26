#!/usr/bin/env node
import { realpathSync } from "node:fs";
import { stdout as output } from "node:process";
import { fileURLToPath } from "node:url";
import { ControleGastosApiClient } from "./api-client.js";
import { loadConfig } from "./config.js";
import { type AskFn, runInteractiveLogin } from "./login-flow.js";
import { FileSessionStore } from "./session-store.js";
import { askVisible, readSecretWithBash } from "./terminal-prompt.js";

function createAsk(): AskFn {
  return async (prompt, options) => {
    if (options?.secret) {
      return readSecretWithBash(prompt);
    }
    return askVisible(prompt);
  };
}

export type LoginCliDeps = {
  env?: NodeJS.ProcessEnv;
  ask?: AskFn;
  write?: (text: string) => void;
  fetchImpl?: typeof fetch;
};

/** Login interativo do terminal; devolve o código de saída do processo. */
export async function runLoginCli(deps: LoginCliDeps = {}): Promise<number> {
  const config = loadConfig(deps.env);
  const write = deps.write ?? ((text: string) => output.write(text));
  const sessionStore = new FileSessionStore(config.sessionPath);
  const client = new ControleGastosApiClient({
    apiBaseUrl: config.apiBaseUrl,
    sessionStore,
    fetchImpl: deps.fetchImpl,
  });

  write(`API: ${config.apiBaseUrl}\n`);
  write(`Sessão: ${config.sessionPath}\n`);
  const result = await runInteractiveLogin({
    client,
    ask: deps.ask ?? createAsk(),
    writeLine: (line) => write(`${line}\n`),
  });
  return result.ok ? 0 : 1;
}

/** Só executa quando chamado como programa (bin ou `tsx`), nunca ao ser importado. */
function isEntryPoint(): boolean {
  const entry = process.argv[1];
  if (!entry) return false;
  try {
    return realpathSync(entry) === realpathSync(fileURLToPath(import.meta.url));
  } catch {
    return false;
  }
}

if (isEntryPoint()) {
  runLoginCli()
    .then((code) => {
      process.exitCode = code;
    })
    .catch((error: unknown) => {
      const message = error instanceof Error ? error.message : "login-cli failed";
      console.error(message);
      process.exit(1);
    });
}
