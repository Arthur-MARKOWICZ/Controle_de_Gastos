import { mkdtempSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { FileSessionStore, MemorySessionStore, type McpSession } from "./session-store.js";

const sample: McpSession = {
  accessToken: "access",
  refreshToken: "refresh",
  refreshCookieName: "refresh_token",
  apiBaseUrl: "http://localhost:8080",
};

describe("MemorySessionStore", () => {
  it("saves, loads and clears a session", () => {
    const store = new MemorySessionStore();
    expect(store.load()).toBeNull();
    store.save(sample);
    expect(store.load()).toEqual(sample);
    store.clear();
    expect(store.load()).toBeNull();
  });
});

describe("FileSessionStore", () => {
  let dir: string;

  afterEach(() => {
    if (dir) {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("persists session with restricted permissions and rejects garbage", () => {
    dir = mkdtempSync(join(tmpdir(), "cg-mcp-session-"));
    const path = join(dir, "session.json");
    const store = new FileSessionStore(path);

    store.save(sample);
    expect(store.load()).toEqual(sample);
    expect(statSync(path).mode & 0o777).toBe(0o600);

    writeFileSync(path, '{"accessToken":"only"}\n');
    expect(store.load()).toBeNull();

    store.clear();
    expect(store.load()).toBeNull();
  });

  it("returns null when the file is missing", () => {
    dir = mkdtempSync(join(tmpdir(), "cg-mcp-session-"));
    const store = new FileSessionStore(join(dir, "missing.json"));
    expect(store.load()).toBeNull();
  });
});
