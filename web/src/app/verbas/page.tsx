"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { VerbasPage } from "../../components/VerbasPage/VerbasPage";

export default function VerbasRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <VerbasPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
