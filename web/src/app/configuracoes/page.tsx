"use client";

import { ProtectedPage } from "../../auth/ProtectedPage";
import { SettingsPage } from "../../components/SettingsPage/SettingsPage";

export default function SettingsRoute() {
  return (
    <ProtectedPage>
      {(user, auth) => <SettingsPage email={user.email} onLogout={() => void auth.logout()} />}
    </ProtectedPage>
  );
}
