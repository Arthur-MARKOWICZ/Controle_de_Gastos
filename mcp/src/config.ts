export type McpConfig = {
  apiBaseUrl: string;
  accessToken: string;
};

export function loadConfig(
  env: NodeJS.ProcessEnv = process.env,
): McpConfig {
  const apiBaseUrl = (env.CONTROLE_GASTOS_API_BASE_URL ?? "").trim().replace(/\/+$/, "");
  const accessToken = (env.CONTROLE_GASTOS_ACCESS_TOKEN ?? "").trim();

  if (!apiBaseUrl) {
    throw new Error("CONTROLE_GASTOS_API_BASE_URL is required");
  }
  if (!/^https?:\/\//i.test(apiBaseUrl)) {
    throw new Error("CONTROLE_GASTOS_API_BASE_URL must be an http(s) origin");
  }
  if (!accessToken) {
    throw new Error("CONTROLE_GASTOS_ACCESS_TOKEN is required");
  }

  return { apiBaseUrl, accessToken };
}
