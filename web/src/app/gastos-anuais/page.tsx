"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { AnnualExpensesPage } from "../../components/AnnualExpensesPage/AnnualExpensesPage";

export default function AnnualExpensesRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <AnnualExpensesPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
