import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { describe, expect, it, vi } from "vitest";
import { ControleGastosApiClient } from "./api-client.js";
import { MCP_TOOL_NAMES, createMcpServer } from "./server.js";
import { MemorySessionStore, type McpSession } from "./session-store.js";

const SESSION: McpSession = {
  accessToken: "jwt.access.secreto",
  refreshToken: "refresh-opaco-secreto",
  refreshCookieName: "refresh_token",
  apiBaseUrl: "http://api.test",
};
const ENVELOPE_ID = "3f1c2b8e-1d2a-4c5b-9e7f-0a1b2c3d4e5f";

async function connect(fetchImpl: typeof fetch, session: McpSession | null = SESSION) {
  const store = new MemorySessionStore(session);
  const api = new ControleGastosApiClient({ apiBaseUrl: "http://api.test", sessionStore: store, fetchImpl });
  const [clientSide, serverSide] = InMemoryTransport.createLinkedPair();
  await createMcpServer(api).connect(serverSide);
  const client = new Client({ name: "teste", version: "0.0.0" });
  await client.connect(clientSide);
  return { client, store };
}

type ToolReply = { isError?: boolean; content: Array<{ type: string; text?: string }> };

async function call(client: Client, name: string, args: Record<string, unknown> = {}): Promise<ToolReply> {
  try {
    return (await client.callTool({ name, arguments: args })) as ToolReply;
  } catch (error) {
    return { isError: true, content: [{ type: "text", text: error instanceof Error ? error.message : String(error) }] };
  }
}

const textOf = (reply: ToolReply) => reply.content.map((part) => part.text ?? "").join("\n");

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("MCP tool surface", () => {
  it("exposes only the allowed tools for this ADR cut", () => {
    expect([...MCP_TOOL_NAMES].sort()).toEqual(
      [
        "auth_status",
        "create_envelope",
        "get_envelope",
        "get_ledger_summary",
        "get_user_email",
        "list_envelope_expenses",
        "list_envelopes",
        "list_expenses",
        "login",
        "logout",
        "register_expense",
        "update_envelope",
        "verify_mfa",
      ].sort(),
    );
  });

  it("does not expose user mutation or expense edit/delete tools", () => {
    const forbidden = [
      "update_user",
      "get_user",
      "get_current_user",
      "delete_expense",
      "update_expense",
      "archive_envelope",
      "register_contribution",
      "update_income",
      "get_income",
    ];
    for (const name of forbidden) {
      expect(MCP_TOOL_NAMES).not.toContain(name);
    }
  });
});

describe("tools do MCP", () => {
  it("register_expense envia o valor em string de duas casas para a verba", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(json(201, { id: "lanc-1" }));
    const { client } = await connect(fetchImpl);

    const reply = await call(client, "register_expense", {
      envelopeId: ENVELOPE_ID,
      amount: { amount: "10.50", currency: "BRL" },
      occurredAt: "2026-11-05",
    });

    expect(reply.isError).toBeFalsy();
    const [url, init] = fetchImpl.mock.calls[0];
    expect(String(url)).toBe(`http://api.test/api/v1/envelopes/${ENVELOPE_ID}/entries`);
    expect(JSON.parse(String(init.body)).amount).toEqual({ amount: "10.50", currency: "BRL" });
  });

  it("auth_status não expõe tokens da sessão", async () => {
    const { client } = await connect(vi.fn());

    const text = textOf(await call(client, "auth_status"));

    expect(text).not.toContain(SESSION.accessToken);
    expect(text).not.toContain(SESSION.refreshToken);
  });

  it("login com senha errada devolve erro e não salva sessão", async () => {
    const { client, store } = await connect(vi.fn().mockResolvedValue(json(401, { code: "AUTHENTICATION_FAILED" })), null);

    const reply = await call(client, "login", { email: "ana@example.com", password: "senha errada" });

    expect(reply.isError).toBe(true);
    expect(JSON.parse(textOf(reply))).toMatchObject({ status: 401, body: { code: "AUTHENTICATION_FAILED" } });
    expect(textOf(reply)).not.toContain("senha errada");
    expect(store.load()).toBeNull();
  });

  it("valor com uma casa decimal é recusado antes de chamar a API", async () => {
    const fetchImpl = vi.fn();
    const { client } = await connect(fetchImpl);

    const reply = await call(client, "register_expense", {
      envelopeId: ENVELOPE_ID,
      amount: { amount: "10.5", currency: "BRL" },
      occurredAt: "2026-11-05",
    });

    expect(reply.isError).toBe(true);
    expect(textOf(reply)).toMatch(/two places/);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("id de verba que não é UUID é recusado antes de chamar a API", async () => {
    const fetchImpl = vi.fn();
    const { client } = await connect(fetchImpl);

    const reply = await call(client, "get_envelope", { id: "../users/me" });

    expect(reply.isError).toBe(true);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("sem sessão salva pede para chamar login", async () => {
    const { client } = await connect(vi.fn(), null);

    const reply = await call(client, "list_envelopes");

    expect(reply.isError).toBe(true);
    expect(JSON.parse(textOf(reply)).code).toBe("AUTH_REQUIRED");
  });

  it("verba de outro titular devolve o status da API sem dados da verba", async () => {
    const { client } = await connect(vi.fn().mockResolvedValue(json(404, { code: "ENVELOPE_NOT_FOUND" })));

    const reply = await call(client, "get_envelope", { id: ENVELOPE_ID });

    expect(reply.isError).toBe(true);
    expect(JSON.parse(textOf(reply))).toEqual({ status: 404, body: { code: "ENVELOPE_NOT_FOUND" } });
  });
});

