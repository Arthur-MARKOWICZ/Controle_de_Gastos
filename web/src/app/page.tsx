"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { ProtectedPage } from "../auth/ProtectedPage";
import { Dashboard } from "../components/Dashboard/Dashboard";

function loginNoticeFrom(notice: string | null): string | null {
  if (notice === "mfa-enabled") return "MFA ativado com sucesso. Entre novamente para continuar.";
  if (notice === "mfa-disabled") return "MFA desativado com sucesso. Entre novamente para continuar.";
  return null;
}

function HomeContent() {
  const searchParams = useSearchParams();
  const loginNotice = loginNoticeFrom(searchParams.get("notice"));
  return (
    <ProtectedPage loginNotice={loginNotice}>
      {(user, auth) => <Dashboard email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}

export default function Home() {
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>}>
      <HomeContent />
    </Suspense>
  );
}
