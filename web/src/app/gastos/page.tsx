"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { HistoryPage } from "../../components/HistoryPage/HistoryPage";

export default function ExpensesRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <HistoryPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
