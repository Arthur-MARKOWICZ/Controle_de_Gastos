"use client";

import { useMemo } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient } from "../../lib/api";
import { NotificationSettings } from "./NotificationSettings";

/** Liga a seção de notificações à sessão atual. */
export function NotificationSettingsSection() {
  const { client } = useAuth();
  const api = useMemo(() => createApiClient(client), [client]);
  return <NotificationSettings api={api} />;
}
