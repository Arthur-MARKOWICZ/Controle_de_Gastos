import { chmodSync, mkdirSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { homedir } from "node:os";

export type McpSession = {
  accessToken: string;
  refreshToken: string;
  refreshCookieName: string;
  apiBaseUrl: string;
};

export interface SessionStore {
  load(): McpSession | null;
  save(session: McpSession): void;
  clear(): void;
}

export class MemorySessionStore implements SessionStore {
  private session: McpSession | null = null;

  constructor(initial?: McpSession | null) {
    this.session = initial ?? null;
  }

  load(): McpSession | null {
    return this.session;
  }

  save(session: McpSession): void {
    this.session = { ...session };
  }

  clear(): void {
    this.session = null;
  }
}

export function defaultSessionPath(): string {
  return join(homedir(), ".config", "controle-gastos", "mcp-session.json");
}

export class FileSessionStore implements SessionStore {
  constructor(private readonly filePath: string) {}

  load(): McpSession | null {
    try {
      const raw = readFileSync(this.filePath, "utf8");
      const parsed = JSON.parse(raw) as Partial<McpSession>;
      if (
        typeof parsed.accessToken !== "string" ||
        typeof parsed.refreshToken !== "string" ||
        typeof parsed.refreshCookieName !== "string" ||
        typeof parsed.apiBaseUrl !== "string"
      ) {
        return null;
      }
      return {
        accessToken: parsed.accessToken,
        refreshToken: parsed.refreshToken,
        refreshCookieName: parsed.refreshCookieName,
        apiBaseUrl: parsed.apiBaseUrl,
      };
    } catch {
      return null;
    }
  }

  save(session: McpSession): void {
    mkdirSync(dirname(this.filePath), { recursive: true });
    writeFileSync(this.filePath, `${JSON.stringify(session, null, 2)}\n`, {
      encoding: "utf8",
      mode: 0o600,
    });
    try {
      chmodSync(this.filePath, 0o600);
    } catch {
      // best-effort on platforms that ignore mode
    }
  }

  clear(): void {
    try {
      unlinkSync(this.filePath);
    } catch {
      // already absent
    }
  }
}
