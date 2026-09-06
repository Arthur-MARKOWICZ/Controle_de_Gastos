"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AuthProvider, useAuth } from "../../../auth/auth-context";
import { AuthScreen } from "../../../components/AuthScreen/AuthScreen";
import { SecuritySettings } from "../../../components/SecuritySettings/SecuritySettings";

function connectionNoticeFrom(searchParams: URLSearchParams): string | null {
  const connected = searchParams.get("connected");
  if (connected === "google") return "Conta Google conectada com sucesso.";
  if (connected === "github") return "Conta GitHub conectada com sucesso.";
  if (searchParams.get("connectError")) return "Não foi possível conectar essa conta. Tente novamente.";
  return null;
}

function AuthenticatedSecuritySettings() {
  const auth = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  if (auth.state === "loading") return <main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>;
  if (auth.state !== "authenticated" || !auth.user) {
    return (
      <AuthScreen
        onLogin={auth.login}
        onRegister={auth.register}
        onOAuthLogin={auth.startOAuthLogin}
        externalError={auth.error}
        expired={auth.state === "expired"}
      />
    );
  }
  return (
    <SecuritySettings
      client={auth.client}
      connectionNotice={connectionNoticeFrom(searchParams)}
      onLoggedOut={async () => {
        await auth.logout();
        router.push("/?notice=mfa-enabled");
      }}
    />
  );
}

export default function SecuritySettingsRoute() {
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>}>
      <AuthProvider><AuthenticatedSecuritySettings /></AuthProvider>
    </Suspense>
  );
}
