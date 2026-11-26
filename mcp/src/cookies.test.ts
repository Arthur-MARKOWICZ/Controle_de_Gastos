import { describe, expect, it } from "vitest";
import { parseRefreshCookie, readSetCookieHeaders } from "./cookies.js";

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

  it("pega o cookie de refresh no meio de outros cookies", () => {
    expect(parseRefreshCookie(["theme=dark; Path=/", "refresh_token=r2; Path=/api/v1/auth"])).toEqual({
      name: "refresh_token",
      value: "r2",
    });
  });

  it("ignora cabeçalho sem nome antes do igual", () => {
    expect(parseRefreshCookie(["=abc; Path=/"])).toBeNull();
    expect(parseRefreshCookie(["refresh_token"])).toBeNull();
  });

  it("lista vazia não tem cookie", () => {
    expect(parseRefreshCookie([])).toBeNull();
  });

  it("valor com sinal de igual é preservado inteiro", () => {
    expect(parseRefreshCookie(["refresh_token=abc==; HttpOnly"])).toEqual({ name: "refresh_token", value: "abc==" });
  });
});

describe("readSetCookieHeaders", () => {
  it("lê vários Set-Cookie", () => {
    const headers = new Headers();
    headers.append("Set-Cookie", "a=1");
    headers.append("Set-Cookie", "refresh_token=r; Path=/api/v1/auth");

    expect(readSetCookieHeaders(headers)).toEqual(["a=1", "refresh_token=r; Path=/api/v1/auth"]);
  });

  it("sem Set-Cookie devolve lista vazia", () => {
    expect(readSetCookieHeaders(new Headers())).toEqual([]);
  });

  it("sem getSetCookie usa o cabeçalho único", () => {
    const legacy = { get: (name: string) => (name === "set-cookie" ? "refresh_token=x" : null) } as unknown as Headers;

    expect(readSetCookieHeaders(legacy)).toEqual(["refresh_token=x"]);
  });
});

