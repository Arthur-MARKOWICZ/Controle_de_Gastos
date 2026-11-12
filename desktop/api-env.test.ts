import { describe, expect, it } from "vitest";
import { ApiEnvError, parseApiEnvName, resolveApiBaseUrl } from "./api-env";

describe("resolveApiBaseUrl", () => {
  it("aceita http://localhost:8080 em local", () => {
    expect(
      resolveApiBaseUrl({
        API_ENV: "local",
        API_BASE_URL_LOCAL: "http://localhost:8080",
        API_BASE_URL_PROD: "https://api.exemplo.com.br",
      }),
    ).toBe("http://localhost:8080");
  });

  it("usa a URL de produção quando API_ENV=prod", () => {
    expect(
      resolveApiBaseUrl({
        API_ENV: "prod",
        API_BASE_URL_LOCAL: "http://localhost:8080",
        API_BASE_URL_PROD: "https://api.exemplo.com.br",
      }),
    ).toBe("https://api.exemplo.com.br");
  });

  it("remove a barra final", () => {
    expect(
      resolveApiBaseUrl({ API_ENV: "local", API_BASE_URL_LOCAL: "http://localhost:8080/" }),
    ).toBe("http://localhost:8080");
  });

  it("falha quando a URL do ambiente escolhido é inválida", () => {
    expect(() =>
      resolveApiBaseUrl({ API_ENV: "local", API_BASE_URL_LOCAL: "localhost:8080" }),
    ).toThrow(ApiEnvError);
  });

  it("falha com protocolo que não é http(s)", () => {
    expect(() =>
      resolveApiBaseUrl({ API_ENV: "prod", API_BASE_URL_PROD: "ftp://api.exemplo.com.br" }),
    ).toThrow(/http ou https/);
  });

  it("falha quando a URL traz caminho ou query", () => {
    expect(() =>
      resolveApiBaseUrl({ API_ENV: "prod", API_BASE_URL_PROD: "https://api.exemplo.com.br/api/v1" }),
    ).toThrow(/só a origem/);
    expect(() =>
      resolveApiBaseUrl({ API_ENV: "prod", API_BASE_URL_PROD: "https://api.exemplo.com.br?x=1" }),
    ).toThrow(/só a origem/);
  });

  it("falha quando a URL do ambiente escolhido está ausente", () => {
    expect(() =>
      resolveApiBaseUrl({ API_ENV: "prod", API_BASE_URL_LOCAL: "http://localhost:8080" }),
    ).toThrow(/API_BASE_URL_PROD é obrigatória/);
  });

  it("não valida o ambiente que não foi escolhido", () => {
    expect(
      resolveApiBaseUrl({
        API_ENV: "local",
        API_BASE_URL_LOCAL: "http://localhost:8080",
        API_BASE_URL_PROD: "isso-nao-e-url",
      }),
    ).toBe("http://localhost:8080");
  });
});

describe("parseApiEnvName", () => {
  it("aceita local e prod em qualquer caixa", () => {
    expect(parseApiEnvName("local")).toBe("local");
    expect(parseApiEnvName(" PROD ")).toBe("prod");
  });

  it("recusa ambiente desconhecido ou ausente", () => {
    expect(() => parseApiEnvName("staging")).toThrow(ApiEnvError);
    expect(() => parseApiEnvName(undefined)).toThrow(ApiEnvError);
  });
});
