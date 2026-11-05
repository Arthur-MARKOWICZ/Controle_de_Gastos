import { describe, expect, it } from "vitest";
import { parseRefreshCookie } from "./cookies.js";

describe("parseRefreshCookie", () => {
  it("parses local and secure refresh cookie names", () => {
    expect(
      parseRefreshCookie([
        "refresh_token=abc.def; Path=/api/v1/auth; HttpOnly; SameSite=Strict",
      ]),
    ).toEqual({ name: "refresh_token", value: "abc.def" });

    expect(
      parseRefreshCookie([
        "__Secure-refresh_token=xyz; Path=/api/v1/auth; Secure; HttpOnly; SameSite=Strict",
      ]),
    ).toEqual({ name: "__Secure-refresh_token", value: "xyz" });
  });

  it("ignores unrelated cookies and empty values", () => {
    expect(parseRefreshCookie(["session=1; Path=/"])).toBeNull();
    expect(parseRefreshCookie(["refresh_token=; Path=/api/v1/auth"])).toBeNull();
  });
});
