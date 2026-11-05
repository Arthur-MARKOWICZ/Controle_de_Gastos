import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";

describe("loadConfig", () => {
  it("requires api base url and access token", () => {
    expect(() => loadConfig({})).toThrow(/CONTROLE_GASTOS_API_BASE_URL/);
    expect(() =>
      loadConfig({ CONTROLE_GASTOS_API_BASE_URL: "http://localhost:8080" }),
    ).toThrow(/CONTROLE_GASTOS_ACCESS_TOKEN/);
  });

  it("rejects non-http base urls", () => {
    expect(() =>
      loadConfig({
        CONTROLE_GASTOS_API_BASE_URL: "ftp://example.com",
        CONTROLE_GASTOS_ACCESS_TOKEN: "token",
      }),
    ).toThrow(/http\(s\)/);
  });

  it("trims trailing slash from the base url", () => {
    const config = loadConfig({
      CONTROLE_GASTOS_API_BASE_URL: "http://localhost:8080/",
      CONTROLE_GASTOS_ACCESS_TOKEN: " tok ",
    });
    expect(config).toEqual({
      apiBaseUrl: "http://localhost:8080",
      accessToken: "tok",
    });
  });
});
