"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { CarteiraPage } from "../../components/CarteiraPage/CarteiraPage";

export default function CarteiraRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <CarteiraPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
