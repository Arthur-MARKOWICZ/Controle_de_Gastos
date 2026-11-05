import { describe, expect, it } from "vitest";
import { MCP_TOOL_NAMES } from "./server.js";

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
