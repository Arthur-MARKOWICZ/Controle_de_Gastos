"use client";

import { useMemo } from "react";
import { AuthClient } from "../../auth/auth-client";
import { PasswordRecoveryRequest } from "../../components/PasswordRecovery/PasswordRecovery";

export default function PasswordRecoveryPage() {
  const client = useMemo(() => new AuthClient(), []);
  return <PasswordRecoveryRequest onRequest={(email) => client.requestPasswordReset(email)} />;
}
