"use client";

import { Suspense } from "react";
import { AuthProvider, useAuth } from "../../auth/auth-context";
import { AuthScreen } from "../../components/AuthScreen/AuthScreen";
import { SettingsPage } from "../../components/SettingsPage/SettingsPage";

function AuthenticatedSettings() {
  const auth = useAuth();
  if (auth.state === "loading") return <main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>;
  if (auth.state !== "authenticated" || !auth.user) {
    return <AuthScreen onLogin={auth.login} onRegister={auth.register} externalError={auth.error} expired={auth.state === "expired"} />;
  }
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Carregando…</p></main>}>
      <SettingsPage email={auth.user.email} onLogout={() => void auth.logout()} />
    </Suspense>
  );
}

export default function SettingsRoute() {
  return <AuthProvider><AuthenticatedSettings /></AuthProvider>;
}
