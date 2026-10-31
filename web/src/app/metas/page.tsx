"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { MetasPage } from "../../components/MetasPage/MetasPage";

export default function GoalsRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <MetasPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
