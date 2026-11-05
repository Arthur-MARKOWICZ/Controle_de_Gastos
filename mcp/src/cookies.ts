/** Extrai o cookie de refresh emitido por /auth/login|refresh|mfa/verify. */
export function parseRefreshCookie(
  setCookieHeaders: readonly string[],
): { name: string; value: string } | null {
  for (const header of setCookieHeaders) {
    const pair = header.split(";", 1)[0] ?? "";
    const eq = pair.indexOf("=");
    if (eq <= 0) {
      continue;
    }
    const name = pair.slice(0, eq).trim();
    const value = pair.slice(eq + 1).trim();
    if (!value) {
      continue;
    }
    if (name === "refresh_token" || name === "__Secure-refresh_token" || /refresh/i.test(name)) {
      return { name, value };
    }
  }
  return null;
}

export function readSetCookieHeaders(headers: Headers): string[] {
  const withGetter = headers as Headers & { getSetCookie?: () => string[] };
  if (typeof withGetter.getSetCookie === "function") {
    return withGetter.getSetCookie();
  }
  const single = headers.get("set-cookie");
  return single ? [single] : [];
}
