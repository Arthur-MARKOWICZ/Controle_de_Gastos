"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AuthClient, AuthError, type CurrentUser } from "./auth-client";

type AuthState = "loading" | "anonymous" | "authenticated" | "expired" | "mfa-pending" | "mfa-recovery-setup";

const GENERIC_MFA_ERROR = "Não foi possível concluir a autenticação. Tente novamente.";

type AuthContextValue = {
  state: AuthState;
  user: CurrentUser | null;
  error: string | null;
  client: AuthClient;
  mfaChallengeId: string | null;
  mfaRestrictedToken: string | null;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  verifyMfa(code: string): Promise<void>;
  verifyRecoveryCode(code: string): Promise<void>;
  finishMfaRecoverySetup(): void;
  startOAuthLogin(provider: "google" | "github"): void;
  completeOAuthCallback(params: URLSearchParams): Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider(
  { children, skipInitialRestore = false }: { children: React.ReactNode; skipInitialRestore?: boolean },
) {
  const [state, setState] = useState<AuthState>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mfaChallengeId, setMfaChallengeId] = useState<string | null>(null);
  const [mfaRestrictedToken, setMfaRestrictedToken] = useState<string | null>(null);
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
    if (skipInitialRestore) return;
    let active = true;
    void client.restore().then((restored) => {
      if (!active) return;
      setUser(restored);
      setState(restored ? "authenticated" : "anonymous");
    });
    return () => { active = false; };
  }, [client, skipInitialRestore]);

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
    mfaChallengeId,
    mfaRestrictedToken,
    async login(email, password) {
      setError(null);
      try {
        const authenticated = await client.login(email, password);
        setUser(authenticated);
        setState("authenticated");
        notifyTabs();
      } catch (cause) {
        if (cause instanceof AuthError && cause.kind === "mfa-required") {
          setMfaChallengeId(cause.challengeId ?? null);
          setState("mfa-pending");
          return;
        }
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
      setMfaChallengeId(null);
      setMfaRestrictedToken(null);
      try {
        await client.logout();
      } catch {
        // O estado local já foi limpo; a sessão remota expirará se a API estiver indisponível.
      } finally {
        notifyTabs();
      }
    },
    async verifyMfa(code) {
      setError(null);
      try {
        const authenticated = await client.verifyMfa(mfaChallengeId ?? "", code);
        setUser(authenticated);
        setMfaChallengeId(null);
        setState("authenticated");
        notifyTabs();
      } catch (cause) {
        setError(GENERIC_MFA_ERROR);
        throw cause;
      }
    },
    async verifyRecoveryCode(code) {
      setError(null);
      try {
        const restrictedToken = await client.verifyRecoveryCode(mfaChallengeId ?? "", code);
        setMfaRestrictedToken(restrictedToken);
        setState("mfa-recovery-setup");
      } catch (cause) {
        setError(GENERIC_MFA_ERROR);
        throw cause;
      }
    },
    finishMfaRecoverySetup() {
      setMfaChallengeId(null);
      setMfaRestrictedToken(null);
      setError(null);
      setUser(null);
      setState("anonymous");
    },
    startOAuthLogin(provider) {
      setError(null);
      void client.startOAuth(provider).catch(() => {
        setError("Não foi possível iniciar o login com esse provedor.");
      });
    },
    async completeOAuthCallback(params) {
      setError(null);
      if (params.get("error")) {
        setError("Não foi possível entrar com esse provedor. Tente novamente.");
        setState("anonymous");
        return;
      }
      if (params.get("mfaRequired") === "true") {
        setMfaChallengeId(params.get("challengeId"));
        setState("mfa-pending");
        return;
      }
      await restore();
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth deve estar dentro de AuthProvider");
  return value;
}
