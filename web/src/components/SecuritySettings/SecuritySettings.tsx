"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import type { AuthClient, LoginMethods, MfaStatus, OAuthProviderName } from "../../auth/auth-client";
import { MfaSettings } from "../MfaSettings/MfaSettings";
import styles from "./SecuritySettings.module.css";

type Props = {
  client: AuthClient;
  connectionNotice?: string | null;
  onLoggedOut(): void;
};

export function SecuritySettings({ client, connectionNotice, onLoggedOut }: Props) {
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
      <div className={styles.backRow}>
        <Link className={styles.back} href="/configuracoes">← Voltar</Link>
      </div>
      <LinkedAccountsPanel client={client} connectionNotice={connectionNotice} />
      {status?.status === "ENABLED"
        ? <MfaEnabledPanel client={client} onLoggedOut={onLoggedOut} />
        : <MfaSettings client={client} onComplete={onLoggedOut} />}
    </>
  );
}

const PROVIDER_LABELS: Record<OAuthProviderName, string> = { google: "Google", github: "GitHub" };
const ALL_PROVIDERS: OAuthProviderName[] = ["google", "github"];

function LinkedAccountsPanel({ client, connectionNotice }: { client: AuthClient; connectionNotice?: string | null }) {
  const [status, setStatus] = useState<LoginMethods | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(connectionNotice ?? null);
  const [busyProvider, setBusyProvider] = useState<OAuthProviderName | null>(null);
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [password, setPassword] = useState("");
  const [passwordBusy, setPasswordBusy] = useState(false);

  useEffect(() => {
    let active = true;
    client.loginMethods()
      .then((result) => { if (active) setStatus(result); })
      .catch(() => { if (active) setMessage("Não foi possível carregar as contas conectadas."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [client]);

  if (loading || !status) return null;

  const isOnlyRemainingMethod = (provider: OAuthProviderName) =>
    !status.hasPassword && status.linkedProviders.length === 1 && status.linkedProviders[0] === provider;

  async function connect(provider: OAuthProviderName) {
    setMessage(null);
    setBusyProvider(provider);
    try {
      await client.connectOAuth(provider);
    } catch {
      setMessage("Não foi possível iniciar a conexão. Tente novamente.");
      setBusyProvider(null);
    }
  }

  async function disconnect(provider: OAuthProviderName) {
    setMessage(null);
    setBusyProvider(provider);
    try {
      await client.unlinkProvider(provider);
      setStatus((current) => current && {
        ...current,
        linkedProviders: current.linkedProviders.filter((linked) => linked !== provider),
      });
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Não foi possível desconectar.");
    } finally {
      setBusyProvider(null);
    }
  }

  async function submitPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordBusy(true);
    setMessage(null);
    try {
      await client.addPassword(password);
      setStatus((current) => current && { ...current, hasPassword: true });
      setShowPasswordForm(false);
      setPassword("");
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Não foi possível cadastrar a senha.");
    } finally {
      setPasswordBusy(false);
    }
  }

  return (
    <section className={styles.card} aria-labelledby="linked-accounts-title">
      <h1 id="linked-accounts-title">Contas conectadas</h1>
      {message && <p className={styles.notice} role="status">{message}</p>}
      <div className={styles.actions}>
        {ALL_PROVIDERS.map((provider) => {
          const linked = status.linkedProviders.includes(provider);
          return linked ? (
            <button key={provider} type="button"
              disabled={busyProvider === provider || isOnlyRemainingMethod(provider)}
              onClick={() => disconnect(provider)}>
              {busyProvider === provider ? "Aguarde…" : `Desconectar ${PROVIDER_LABELS[provider]}`}
            </button>
          ) : (
            <button key={provider} type="button" disabled={busyProvider === provider}
              onClick={() => connect(provider)}>
              {busyProvider === provider ? "Aguarde…" : `Conectar ${PROVIDER_LABELS[provider]}`}
            </button>
          );
        })}
      </div>

      {!status.hasPassword && !showPasswordForm && (
        <button type="button" onClick={() => setShowPasswordForm(true)}>Cadastrar senha</button>
      )}
      {!status.hasPassword && showPasswordForm && (
        <form className={styles.form} onSubmit={submitPassword}>
          <label htmlFor="new-password">Nova senha</label>
          <input id="new-password" type="password" autoComplete="new-password" minLength={12} maxLength={128}
            required value={password} onChange={(event) => setPassword(event.target.value)} />
          <button className={styles.submit} type="submit" disabled={passwordBusy}>
            {passwordBusy ? "Aguarde…" : "Salvar senha"}
          </button>
        </form>
      )}
    </section>
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
