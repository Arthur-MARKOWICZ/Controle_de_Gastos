"use client";

import { FormEvent, useState } from "react";
import styles from "./MfaLoginStep.module.css";

const GENERIC_ERROR = "Não foi possível concluir a autenticação. Tente novamente.";

type Props = {
  onVerify(code: string): Promise<void>;
  onUseRecoveryCode(code: string): Promise<void>;
  externalError?: string | null;
};

export function MfaLoginStep({ onVerify, onUseRecoveryCode, externalError }: Props) {
  const [mode, setMode] = useState<"totp" | "recovery">("totp");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  function selectMode(next: "totp" | "recovery") {
    setMode(next);
    setCode("");
    setMessage(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      if (mode === "totp") await onVerify(code);
      else await onUseRecoveryCode(code);
    } catch {
      setMessage(GENERIC_ERROR);
    } finally {
      setBusy(false);
      setCode("");
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="mfa-title">
        <h1 id="mfa-title">Confirme sua identidade</h1>
        <p className={styles.supporting}>
          {mode === "totp"
            ? "Informe o código do seu aplicativo autenticador."
            : "Informe um dos seus códigos de recuperação, guardados quando o MFA foi ativado."}
        </p>

        <div className={styles.tabs} aria-label="Escolha o método de verificação">
          <button type="button" aria-pressed={mode === "totp"} onClick={() => selectMode("totp")}>
            Código do app
          </button>
          <button type="button" aria-pressed={mode === "recovery"} onClick={() => selectMode("recovery")}>
            Código de recuperação
          </button>
        </div>

        {(message ?? externalError) && <p className={styles.notice} role="status">{message ?? externalError}</p>}

        <form className={styles.form} onSubmit={submit}>
          <label htmlFor="mfa-code">{mode === "totp" ? "Código de 6 dígitos" : "Código de recuperação"}</label>
          <input
            id="mfa-code"
            name="code"
            inputMode={mode === "totp" ? "numeric" : "text"}
            autoComplete="one-time-code"
            maxLength={mode === "totp" ? 6 : 11}
            required
            value={code}
            onChange={(event) => setCode(event.target.value)}
          />
          <button className={styles.submit} type="submit" disabled={busy}>
            {busy ? "Verificando…" : "Confirmar"}
          </button>
        </form>
      </section>
    </main>
  );
}
