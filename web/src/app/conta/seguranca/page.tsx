"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ProtectedPage } from "../../../auth/ProtectedPage";
import { SecuritySettings } from "../../../components/SecuritySettings/SecuritySettings";

function connectionNoticeFrom(searchParams: URLSearchParams): string | null {
  const connected = searchParams.get("connected");
  if (connected === "google") return "Conta Google conectada com sucesso.";
  if (connected === "github") return "Conta GitHub conectada com sucesso.";
  if (searchParams.get("connectError")) return "Não foi possível conectar essa conta. Tente novamente.";
  return null;
}

function SecuritySettingsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  return (
    <ProtectedPage>
      {(user, auth) => (
        <SecuritySettings
          client={auth.client}
          email={user.email}
          connectionNotice={connectionNoticeFrom(searchParams)}
          onLogout={() => void auth.logout()}
          onLoggedOut={async (notice) => {
            await auth.logout();
            router.push(`/?notice=${notice}`);
          }}
        />
      )}
    </ProtectedPage>
  );
}

export default function SecuritySettingsRoute() {
  return (
    <Suspense fallback={<main aria-busy="true"><p role="status">Restaurando sua sessão…</p></main>}>
      <SecuritySettingsContent />
    </Suspense>
  );
}
