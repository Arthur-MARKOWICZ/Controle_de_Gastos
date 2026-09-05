"use client";

import { Suspense, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AuthProvider, useAuth } from "../../../auth/auth-context";
import { MfaLoginStep } from "../../../components/MfaLoginStep/MfaLoginStep";

function OAuthCallback() {
  const auth = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void auth.completeOAuthCallback(searchParams);
  }, [auth, searchParams]);

  useEffect(() => {
    if (auth.state === "authenticated") router.replace("/");
  }, [auth.state, router]);

  if (auth.state === "mfa-pending") {
    return <MfaLoginStep onVerify={auth.verifyMfa} onUseRecoveryCode={auth.verifyRecoveryCode} externalError={auth.error} />;
  }
  if (auth.state === "anonymous" && auth.error) {
    return <main><p role="alert">{auth.error}</p></main>;
  }
  return <main aria-busy="true"><p role="status">Concluindo login…</p></main>;
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Concluindo login…</p></main>}>
      <AuthProvider skipInitialRestore><OAuthCallback /></AuthProvider>
    </Suspense>
  );
}
