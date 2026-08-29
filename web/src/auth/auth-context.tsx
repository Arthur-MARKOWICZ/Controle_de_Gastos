"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AuthClient, AuthError, type CurrentUser } from "./auth-client";

type AuthState = "loading" | "anonymous" | "authenticated" | "expired";

type AuthContextValue = {
  state: AuthState;
  user: CurrentUser | null;
  error: string | null;
  client: AuthClient;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const client = useMemo(() => new AuthClient(undefined, () => {
    setUser(null);
    setState("expired");
  }), []);

  const restore = useCallback(async () => {
    const restored = await client.restore();
    setUser(restored);
    setState(restored ? "authenticated" : "anonymous");
  }, [client]);

  useEffect(() => {
    let active = true;
    void client.restore().then((restored) => {
      if (!active) return;
      setUser(restored);
      setState(restored ? "authenticated" : "anonymous");
    });
    return () => { active = false; };
  }, [client]);

  useEffect(() => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel("controle-gastos-auth");
    channel.onmessage = () => void restore();
    return () => channel.close();
  }, [restore]);

  const notifyTabs = () => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel("controle-gastos-auth");
    channel.postMessage("session-changed");
    channel.close();
  };

  const value: AuthContextValue = {
    state,
    user,
    error,
    client,
    async login(email, password) {
      setError(null);
      try {
        const authenticated = await client.login(email, password);
        setUser(authenticated);
        setState("authenticated");
        notifyTabs();
      } catch (cause) {
        const kind = cause instanceof AuthError ? cause.kind : "unexpected";
        setError(kind === "rate-limit"
          ? "Muitas tentativas. Aguarde alguns minutos."
          : "Não foi possível entrar com os dados informados.");
        throw cause;
      }
    },
    register: (email, password) => client.register(email, password),
    async logout() {
      setUser(null);
      setState("anonymous");
      setError(null);
      try {
        await client.logout();
      } catch {
        // O estado local já foi limpo; a sessão remota expirará se a API estiver indisponível.
      } finally {
        notifyTabs();
      }
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth deve estar dentro de AuthProvider");
  return value;
}
