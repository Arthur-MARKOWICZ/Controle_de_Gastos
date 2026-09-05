"use client";

import { FormEvent, useEffect, useState } from "react";
import type { AuthClient, MfaEnrollmentStart } from "../../auth/auth-client";
import styles from "./MfaSettings.module.css";

type Props = {
  client: AuthClient;
  restrictedToken?: string;
  onComplete(): void;
};

type Step = "password" | "confirm" | "codes";

export function MfaSettings({ client, restrictedToken, onComplete }: Props) {
  const [step, setStep] = useState<Step>("password");
  const [password, setPassword] = useState("");
  const [enrollment, setEnrollment] = useState<MfaEnrollmentStart | null>(null);
  const [code, setCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  useEffect(() => {
    if (!enrollment) return;
    const update = () => {
      const remaining = Math.max(0, Math.round((Date.parse(enrollment.pendingExpiresAt) - Date.now()) / 1000));
      setRemainingSeconds(remaining);
    };
    update();
    const interval = window.setInterval(update, 1000);
    return () => window.clearInterval(interval);
  }, [enrollment]);

  async function submitPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const start = await client.startMfaEnrollment(password, restrictedToken);
      setEnrollment(start);
      setStep("confirm");
    } catch {
      setMessage("Não foi possível iniciar a configuração. Confira a senha e tente novamente.");
    } finally {
      setBusy(false);
      setPassword("");
    }
  }

  async function submitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const codes = await client.confirmMfaEnrollment(code, restrictedToken);
      setRecoveryCodes(codes);
      setStep("codes");
    } catch {
      setMessage("Código inválido ou expirado. Gere um novo QR Code e tente novamente.");
    } finally {
      setBusy(false);
      setCode("");
    }
  }

  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="mfa-settings-title">
        <h1 id="mfa-settings-title">Configurar autenticação em duas etapas</h1>

        {step === "password" && (
          <>
            <p className={styles.supporting}>Confirme sua senha atual para começar.</p>
            {message && <p className={styles.notice} role="status">{message}</p>}
            <form className={styles.form} onSubmit={submitPassword}>
              <label htmlFor="mfa-password">Senha atual</label>
              <input
                id="mfa-password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <button className={styles.submit} type="submit" disabled={busy}>
                {busy ? "Aguarde…" : "Continuar"}
              </button>
            </form>
          </>
        )}

        {step === "confirm" && enrollment && (
          <>
            <p className={styles.supporting}>
              Escaneie o QR Code com seu aplicativo autenticador (Microsoft Authenticator, Google Authenticator
              ou similar) e informe o primeiro código gerado.
            </p>
            {/* eslint-disable-next-line @next/next/no-img-element -- imagem gerada dinamicamente como data URI, não um asset estático */}
            <img
              className={styles.qr}
              src={enrollment.qrImageDataUri}
              alt="QR Code para configurar o aplicativo autenticador"
            />
            <p className={styles.manualKey}>
              Não consegue escanear? Informe esta chave manualmente: <code>{enrollment.manualEntryKey}</code>
            </p>
            <p className={styles.countdown} role="status">
              Expira em {minutes}:{seconds.toString().padStart(2, "0")}
            </p>
            {message && <p className={styles.notice} role="status">{message}</p>}
            <form className={styles.form} onSubmit={submitCode}>
              <label htmlFor="mfa-confirm-code">Código de 6 dígitos</label>
              <input
                id="mfa-confirm-code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                required
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
              <button className={styles.submit} type="submit" disabled={busy}>
                {busy ? "Confirmando…" : "Ativar MFA"}
              </button>
            </form>
          </>
        )}

        {step === "codes" && (
          <>
            <p className={styles.supporting}>
              Guarde estes códigos de recuperação em um local seguro. Cada um pode ser usado uma única vez
              para entrar caso você perca acesso ao aplicativo autenticador. Eles não serão mostrados novamente.
            </p>
            <ul className={styles.codes}>
              {recoveryCodes.map((recoveryCode) => (
                <li key={recoveryCode}><code>{recoveryCode}</code></li>
              ))}
            </ul>
            <button className={styles.submit} type="button" onClick={onComplete}>
              Já guardei meus códigos, entrar novamente
            </button>
          </>
        )}
      </section>
    </main>
  );
}
