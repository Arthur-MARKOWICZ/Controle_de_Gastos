/**
 * Resolução da URL da API em tempo de build (ADR-0029, no espírito do ADR-021).
 *
 * O `vite.config.ts` chama `resolveApiBaseUrl` e injeta o resultado no bundle.
 * URL ausente ou inválida derruba o build: o cliente nunca sobe apontando para
 * um destino que não foi declarado.
 */

export type ApiEnvName = "local" | "prod";

export type ApiEnvInput = {
  API_ENV?: string;
  API_BASE_URL_LOCAL?: string;
  API_BASE_URL_PROD?: string;
};

export class ApiEnvError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ApiEnvError";
  }
}

export function parseApiEnvName(raw: string | undefined): ApiEnvName {
  const value = (raw ?? "").trim().toLowerCase();
  if (value === "local" || value === "prod") {
    return value;
  }
  throw new ApiEnvError(
    `API_ENV deve ser "local" ou "prod" (recebido: ${JSON.stringify(raw ?? "")})`,
  );
}

/** Aceita só origem http(s) sem caminho, query ou fragmento. */
export function normalizeApiBaseUrl(raw: string | undefined, variable: string): string {
  const value = (raw ?? "").trim().replace(/\/+$/, "");
  if (value === "") {
    throw new ApiEnvError(`${variable} é obrigatória`);
  }
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new ApiEnvError(`${variable} não é uma URL válida: ${value}`);
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new ApiEnvError(`${variable} deve usar http ou https: ${value}`);
  }
  if (parsed.pathname !== "/" || parsed.search !== "" || parsed.hash !== "") {
    throw new ApiEnvError(
      `${variable} deve ser só a origem, sem caminho nem query: ${value}`,
    );
  }
  return `${parsed.protocol}//${parsed.host}`;
}

export function resolveApiBaseUrl(env: ApiEnvInput): string {
  const name = parseApiEnvName(env.API_ENV);
  return name === "local"
    ? normalizeApiBaseUrl(env.API_BASE_URL_LOCAL, "API_BASE_URL_LOCAL")
    : normalizeApiBaseUrl(env.API_BASE_URL_PROD, "API_BASE_URL_PROD");
}
