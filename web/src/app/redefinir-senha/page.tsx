"use client";

import { useMemo } from "react";
import { AuthClient } from "../../auth/auth-client";
import { PasswordReset } from "../../components/PasswordRecovery/PasswordRecovery";

export default function PasswordResetPage() {
  const client = useMemo(() => new AuthClient(), []);
  return <PasswordReset onReset={(token, password) => client.resetPassword(token, password)} />;
}
