"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import styles from "./PasswordRecovery.module.css";

type RequestProps = { onRequest(email: string): Promise<void> };
type ResetProps = { onReset(token: string, password: string): Promise<void> };

export function PasswordRecoveryRequest({ onRequest }: RequestProps) {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    try {
      await onRequest(email);
    } catch {
      // A resposta permanece genérica para não revelar se o e-mail existe.
    } finally {
      setBusy(false);
      setSent(true);
    }
  }

  return <RecoveryFrame title="Recupere sua senha" description="Informe seu e-mail. Se houver uma conta para ele, enviaremos um link seguro para redefinir a senha.">
    {sent ? <p className={styles.notice} role="status">Se houver uma conta para este e-mail, enviaremos as instruções em alguns minutos.</p> :
      <form className={styles.form} onSubmit={submit}>
        <label htmlFor="recovery-email">E-mail</label>
        <input id="recovery-email" type="email" autoComplete="email" maxLength={254} required value={email} onChange={(event) => setEmail(event.target.value)} />
        <button type="submit" disabled={busy}>{busy ? "Enviando…" : "Enviar link de recuperação"}</button>
      </form>}
    <Link className={styles.back} href="/">Voltar para entrar</Link>
  </RecoveryFrame>;
}

export function PasswordReset({ onReset }: ResetProps) {
  const [token, setToken] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1));
    const value = params.get("token");
    const timer = window.setTimeout(() => {
      setToken(value);
      if (value) window.history.replaceState(null, "", "/redefinir-senha");
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    if (password !== confirmation) {
      setMessage("As senhas precisam ser iguais.");
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await onReset(token, password);
      setMessage("Senha alterada. Entre novamente com a nova senha.");
      setPassword("");
      setConfirmation("");
    } catch {
      setMessage("Este link é inválido ou expirou. Solicite uma nova recuperação.");
    } finally {
      setBusy(false);
    }
  }

  return <RecoveryFrame title="Defina uma nova senha" description="O link é válido por 15 minutos e pode ser usado uma única vez.">
    {!token ? <p className={styles.notice} role="status">Este link é inválido ou expirou. Solicite uma nova recuperação.</p> :
      <form className={styles.form} onSubmit={submit}>
        <label htmlFor="new-password">Nova senha</label>
        <input id="new-password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={password} onChange={(event) => setPassword(event.target.value)} />
        <label htmlFor="confirm-password">Confirme a nova senha</label>
        <input id="confirm-password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />
        <p className={styles.hint}>Use de 12 a 128 caracteres.</p>
        <button type="submit" disabled={busy}>{busy ? "Alterando…" : "Alterar senha"}</button>
      </form>}
    {message && <p className={styles.notice} role="status">{message}</p>}
    <Link className={styles.back} href="/">Voltar para entrar</Link>
  </RecoveryFrame>;
}

function RecoveryFrame({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <main className={styles.page}><section className={styles.card} aria-labelledby="recovery-title">
    <Link className={styles.brand} href="/" aria-label="Verbas, voltar para entrar"><span aria-hidden="true">V</span> Verbas</Link>
    <h1 id="recovery-title">{title}</h1>
    <p className={styles.description}>{description}</p>
    {children}
  </section></main>;
}
