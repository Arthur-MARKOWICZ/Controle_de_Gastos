"use client";

import { Suspense } from "react";
import { useRouter } from "next/navigation";
import { AuthProvider, useAuth } from "../../../auth/auth-context";
import { AuthScreen } from "../../../components/AuthScreen/AuthScreen";
import { SecuritySettings } from "../../../components/SecuritySettings/SecuritySettings";

function AuthenticatedSecuritySettings() {
  const auth = useAuth();
  const router = useRouter();
  if (auth.state === "loading") return <main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>;
  if (auth.state !== "authenticated" || !auth.user) {
    return <AuthScreen onLogin={auth.login} onRegister={auth.register} externalError={auth.error} expired={auth.state === "expired"} />;
  }
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Carregando…</p></main>}>
      <SecuritySettings
        client={auth.client}
        onLoggedOut={async () => {
          await auth.logout();
          router.push("/?notice=mfa-enabled");
        }}
      />
    </Suspense>
  );
}

export default function SecuritySettingsRoute() {
  return <AuthProvider><AuthenticatedSecuritySettings /></AuthProvider>;
}
