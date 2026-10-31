"use client";

import Link from "next/link";
import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AuthProvider, useAuth } from "../../../auth/auth-context";
import { MfaLoginStep } from "../../../components/MfaLoginStep/MfaLoginStep";
import { Notice } from "../../../components/ui/Notice";

const CALLBACK_TIMEOUT_MS = 15_000;

function CallbackError({ message }: { message: string }) {
  return (
    <main style={{ maxWidth: "28rem", margin: "4rem auto", padding: "0 1rem" }}>
      <Notice tone="danger">{message}</Notice>
      <p style={{ marginTop: "1rem" }}>
        <Link href="/">Voltar ao login</Link>
      </p>
    </main>
  );
}

function OAuthCallback() {
  const auth = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const started = useRef(false);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void auth.completeOAuthCallback(searchParams);
  }, [auth, searchParams]);

  useEffect(() => {
    if (auth.state === "authenticated") router.replace("/");
  }, [auth.state, router]);

  useEffect(() => {
    if (auth.state !== "loading") return;
    const timer = window.setTimeout(() => setTimedOut(true), CALLBACK_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [auth.state]);

  if (auth.state === "mfa-pending") {
    return (
      <MfaLoginStep
        onVerify={auth.verifyMfa}
        onUseRecoveryCode={auth.verifyRecoveryCode}
        externalError={auth.error}
      />
    );
  }

  if (auth.state === "anonymous") {
    return (
      <CallbackError
        message={auth.error ?? "Não foi possível concluir o login. Tente novamente."}
      />
    );
  }

  if (timedOut) {
    return <CallbackError message="Não foi possível concluir o login. Tente novamente." />;
  }

  return (
    <main aria-busy="true">
      <p role="status">Concluindo login…</p>
    </main>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <main aria-busy="true">
          <p role="status">Concluindo login…</p>
        </main>
      }
    >
      <AuthProvider skipInitialRestore>
        <OAuthCallback />
      </AuthProvider>
    </Suspense>
  );
}
