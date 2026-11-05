#!/usr/bin/env node
import { stdout as output } from "node:process";
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

async function main(): Promise<void> {
  const config = loadConfig();
  const sessionStore = new FileSessionStore(config.sessionPath);
  const client = new ControleGastosApiClient({
    apiBaseUrl: config.apiBaseUrl,
    sessionStore,
  });

  output.write(`API: ${config.apiBaseUrl}\n`);
  output.write(`Sessão: ${config.sessionPath}\n`);
  const result = await runInteractiveLogin({
    client,
    ask: createAsk(),
    writeLine: (line) => output.write(`${line}\n`),
  });
  process.exitCode = result.ok ? 0 : 1;
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : "login-cli failed";
  console.error(message);
  process.exit(1);
});
