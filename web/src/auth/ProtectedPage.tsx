"use client";

import { Suspense, type ReactNode } from "react";
import { AuthProvider, useAuth, type AuthContextValue } from "./auth-context";
import type { CurrentUser } from "./auth-client";
import { AuthScreen } from "../components/AuthScreen/AuthScreen";
import { MfaLoginStep } from "../components/MfaLoginStep/MfaLoginStep";
import { MfaSettings } from "../components/MfaSettings/MfaSettings";

type Props = {
  loginNotice?: string | null;
  children(user: CurrentUser, auth: AuthContextValue): ReactNode;
};

export function ProtectedPage(props: Props) {
  return <AuthProvider><Gate {...props} /></AuthProvider>;
}

function Gate({ loginNotice = null, children }: Props) {
  const auth = useAuth();
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
        externalError={auth.error}
        notice={loginNotice}
        expired={auth.state === "expired"}
      />
    );
  }
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Carregando…</p></main>}>
      {children(auth.user, auth)}
    </Suspense>
  );
}
