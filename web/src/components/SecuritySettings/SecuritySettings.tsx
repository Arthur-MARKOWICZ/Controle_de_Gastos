"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import type { AuthClient, MfaStatus } from "../../auth/auth-client";
import { MfaSettings } from "../MfaSettings/MfaSettings";
import styles from "./SecuritySettings.module.css";

type Props = {
  client: AuthClient;
  onLoggedOut(): void;
};

export function SecuritySettings({ client, onLoggedOut }: Props) {
  const [status, setStatus] = useState<MfaStatus | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    client.mfaStatus()
      .then((result) => { if (active) setStatus(result); })
      .catch(() => { if (active) setStatus({ status: "DISABLED", pendingExpiresAt: null }); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [client]);

  if (loading) return <main aria-busy="true"><p role="status">Carregando…</p></main>;

  return (
    <>
      <Link className={styles.back} href="/">← Voltar</Link>
      {status?.status === "ENABLED"
        ? <MfaEnabledPanel client={client} onLoggedOut={onLoggedOut} />
        : <MfaSettings client={client} onComplete={onLoggedOut} />}
    </>
  );
}

function MfaEnabledPanel({ client, onLoggedOut }: { client: AuthClient; onLoggedOut(): void }) {
  const [action, setAction] = useState<"none" | "disable" | "regenerate">("none");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      if (action === "disable") {
        await client.disableMfa(password);
        onLoggedOut();
        return;
      }
      const codes = await client.regenerateRecoveryCodes(password);
      setRecoveryCodes(codes);
      setAction("none");
    } catch {
      setMessage("Senha incorreta ou operação indisponível. Tente novamente.");
    } finally {
      setBusy(false);
      setPassword("");
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="mfa-enabled-title">
        <h1 id="mfa-enabled-title">Autenticação em duas etapas</h1>
        <p className={styles.supporting}>A autenticação em duas etapas está ativa na sua conta.</p>

        {recoveryCodes && (
          <>
            <p className={styles.supporting}>
              Novos códigos de recuperação. Guarde-os em local seguro; eles não serão mostrados novamente.
            </p>
            <ul className={styles.codes}>
              {recoveryCodes.map((code) => <li key={code}><code>{code}</code></li>)}
            </ul>
            <button className={styles.submit} type="button" onClick={() => setRecoveryCodes(null)}>
              Concluído
            </button>
          </>
        )}

        {!recoveryCodes && action === "none" && (
          <div className={styles.actions}>
            <button type="button" onClick={() => setAction("regenerate")}>Gerar novos recovery codes</button>
            <button type="button" onClick={() => setAction("disable")}>Desativar MFA</button>
          </div>
        )}

        {!recoveryCodes && action !== "none" && (
          <form className={styles.form} onSubmit={submit}>
            <label htmlFor="mfa-action-password">Senha atual</label>
            <input
              id="mfa-action-password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {message && <p className={styles.notice} role="status">{message}</p>}
            <button className={styles.submit} type="submit" disabled={busy}>
              {busy ? "Aguarde…" : action === "disable" ? "Confirmar desativação" : "Gerar novos códigos"}
            </button>
            <button className={styles.cancel} type="button" onClick={() => setAction("none")}>Cancelar</button>
          </form>
        )}
      </section>
    </main>
  );
}
