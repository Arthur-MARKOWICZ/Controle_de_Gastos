#!/usr/bin/env node
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { ControleGastosApiClient } from "./api-client.js";
import { loadConfig } from "./config.js";
import { createMcpServer } from "./server.js";
import { FileSessionStore } from "./session-store.js";

async function main(): Promise<void> {
  const config = loadConfig();
  const sessionStore = new FileSessionStore(config.sessionPath);
  const client = new ControleGastosApiClient({
    apiBaseUrl: config.apiBaseUrl,
    sessionStore,
  });
  const server = createMcpServer(client);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("controle-gastos MCP server running on stdio");
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : "startup failed";
  console.error(message);
  process.exit(1);
});
