"use client";

import { AuthProvider, useAuth } from "../../auth/auth-context";
import { AuthScreen } from "../../components/AuthScreen/AuthScreen";
import { AnnualExpensesPage } from "../../components/AnnualExpensesPage/AnnualExpensesPage";

function AuthenticatedAnnualExpenses() {
  const auth = useAuth();
  if (auth.state === "loading") return <main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>;
  if (auth.state !== "authenticated" || !auth.user) return <AuthScreen onLogin={auth.login} onRegister={auth.register} externalError={auth.error} expired={auth.state === "expired"} />;
  return <AnnualExpensesPage email={auth.user.email} onLogout={() => void auth.logout()} />;
}

export default function AnnualExpensesRoute() { return <AuthProvider><AuthenticatedAnnualExpenses /></AuthProvider>; }
