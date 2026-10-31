"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { ReportsPage } from "../../components/ReportsPage/ReportsPage";

export default function ReportsRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <ReportsPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
