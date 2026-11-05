import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";

describe("loadConfig", () => {
  it("requires api base url", () => {
    expect(() => loadConfig({})).toThrow(/CONTROLE_GASTOS_API_BASE_URL/);
  });

  it("rejects non-http base urls", () => {
    expect(() =>
      loadConfig({
        CONTROLE_GASTOS_API_BASE_URL: "ftp://example.com",
      }),
    ).toThrow(/http\(s\)/);
  });

  it("trims trailing slash and resolves session path", () => {
    const config = loadConfig({
      CONTROLE_GASTOS_API_BASE_URL: "http://localhost:8080/",
      CONTROLE_GASTOS_SESSION_PATH: "/tmp/mcp-session.json",
    });
    expect(config).toEqual({
      apiBaseUrl: "http://localhost:8080",
      sessionPath: "/tmp/mcp-session.json",
    });
  });

  it("does not require an access token in the environment", () => {
    const config = loadConfig({
      CONTROLE_GASTOS_API_BASE_URL: "https://gastos.example.com",
    });
    expect(config.apiBaseUrl).toBe("https://gastos.example.com");
    expect(config.sessionPath.length).toBeGreaterThan(0);
  });
});
