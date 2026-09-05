"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { AuthProvider, useAuth } from "../auth/auth-context";
import { AuthScreen } from "../components/AuthScreen/AuthScreen";
import { Dashboard } from "../components/Dashboard/Dashboard";
import { MfaLoginStep } from "../components/MfaLoginStep/MfaLoginStep";
import { MfaSettings } from "../components/MfaSettings/MfaSettings";

function AuthenticatedApp() {
  const auth = useAuth();
  const searchParams = useSearchParams();
  const mfaEnabledNotice = searchParams.get("notice") === "mfa-enabled"
    ? "MFA ativado com sucesso. Entre novamente para continuar."
    : null;
  if (auth.state === "loading") return <main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>;
  if (auth.state === "mfa-pending") {
    return <MfaLoginStep onVerify={auth.verifyMfa} onUseRecoveryCode={auth.verifyRecoveryCode} externalError={auth.error} />;
  }
  if (auth.state === "mfa-recovery-setup" && auth.mfaRestrictedToken) {
    return <MfaSettings client={auth.client} restrictedToken={auth.mfaRestrictedToken} onComplete={auth.finishMfaRecoverySetup} />;
  }
  if (auth.state !== "authenticated" || !auth.user) {
    return (
      <AuthScreen
        onLogin={auth.login}
        onRegister={auth.register}
        onOAuthLogin={auth.startOAuthLogin}
        externalError={auth.error ?? mfaEnabledNotice}
        expired={auth.state === "expired"}
      />
    );
  }
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Carregando…</p></main>}>
      <Dashboard email={auth.user.email} onLogout={() => void auth.logout()} />
    </Suspense>
  );
}

export default function Home() {
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>}>
      <AuthProvider><AuthenticatedApp /></AuthProvider>
    </Suspense>
  );
}
