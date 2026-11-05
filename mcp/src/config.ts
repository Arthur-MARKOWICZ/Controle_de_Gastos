import { defaultSessionPath } from "./session-store.js";

export type McpConfig = {
  apiBaseUrl: string;
  sessionPath: string;
};

export function loadConfig(
  env: NodeJS.ProcessEnv = process.env,
): McpConfig {
  const apiBaseUrl = (env.CONTROLE_GASTOS_API_BASE_URL ?? "").trim().replace(/\/+$/, "");
  const sessionPath = (env.CONTROLE_GASTOS_SESSION_PATH ?? "").trim() || defaultSessionPath();

  if (!apiBaseUrl) {
    throw new Error("CONTROLE_GASTOS_API_BASE_URL is required");
  }
  if (!/^https?:\/\//i.test(apiBaseUrl)) {
    throw new Error("CONTROLE_GASTOS_API_BASE_URL must be an http(s) origin");
  }

  return { apiBaseUrl, sessionPath };
}
