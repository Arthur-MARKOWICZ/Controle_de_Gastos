"use client";

import { AuthProvider, useAuth } from "../../auth/auth-context";
import { AuthScreen } from "../../components/AuthScreen/AuthScreen";
import { MetasPage } from "../../components/MetasPage/MetasPage";

function AuthenticatedGoals() {
  const auth = useAuth();
  if (auth.state === "loading") return <main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>;
  if (auth.state !== "authenticated" || !auth.user) return <AuthScreen onLogin={auth.login} onRegister={auth.register} externalError={auth.error} expired={auth.state === "expired"} />;
  return <MetasPage email={auth.user.email} onLogout={() => void auth.logout()} />;
}

export default function GoalsRoute() { return <AuthProvider><AuthenticatedGoals /></AuthProvider>; }
