"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import styles from "./AuthScreen.module.css";
import { ThemeSelector } from "../../theme/ThemeSelector";

type Props = {
  onLogin(email: string, password: string): Promise<void>;
  onRegister(email: string, password: string): Promise<void>;
  onOAuthLogin?(provider: "google" | "github"): void;
  externalError?: string | null;
  expired?: boolean;
};

export function AuthScreen({ onLogin, onRegister, onOAuthLogin, externalError, expired = false }: Props) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      if (mode === "register") {
        await onRegister(email, password);
        setMode("login");
        setPassword("");
        setMessage("Cadastro recebido. Agora você pode entrar com seu e-mail e senha.");
      } else {
        await onLogin(email, password);
      }
    } catch {
      if (mode === "register") setMessage("Revise os dados e tente novamente.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.intro} aria-labelledby="auth-title">
        <a href="#auth-form" className={styles.brand} aria-label="Verbas, ir ao formulário">
          <span aria-hidden="true">V</span> Verbas
        </a>
        <p className={styles.eyebrow}>Dinheiro com destino claro</p>
        <h1 id="auth-title">Organize o mês sem perder de vista a vida real.</h1>
        <p>Reserve sua renda em verbas que acumulam, acompanhe o que está livre e registre gastos com contexto.</p>
        <ul>
          <li>Saldo não usado continua com você.</li>
          <li>Gasto acima do planejado vira alerta, não bloqueio.</li>
          <li>Seus dados financeiros ficam ligados ao seu UUID, nunca ao e-mail.</li>
        </ul>
      </section>

      <section id="auth-form" className={styles.card} aria-labelledby="form-title">
        <div className={styles.themeControl}><ThemeSelector compact /></div>
        <div className={styles.tabs} aria-label="Escolha entre entrar ou criar conta">
          <button type="button" aria-pressed={mode === "login"} onClick={() => { setMode("login"); setMessage(null); setEmail(""); setPassword(""); }}>Entrar</button>
          <button type="button" aria-pressed={mode === "register"} onClick={() => { setMode("register"); setMessage(null); setEmail(""); setPassword(""); }}>Criar conta</button>
        </div>
        <h2 id="form-title">{mode === "login" ? "Entre na sua conta" : "Crie sua conta"}</h2>
        <p className={styles.supporting}>{mode === "login"
          ? "Use o e-mail e a senha cadastrados."
          : "Use de 12 a 128 caracteres. Você poderá recuperar a senha pelo e-mail cadastrado."}</p>

        {expired && <p className={styles.notice} role="status">Sua sessão expirou. Entre novamente.</p>}
        {(message || externalError) && <p className={styles.notice} role="status">{message ?? externalError}</p>}

        <form onSubmit={submit}>
          <label htmlFor="email">E-mail</label>
          <input id="email" name="email" type="email" autoComplete="email" maxLength={254} required
            value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="password">Senha</label>
          <input id="password" name="password" type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            minLength={12} maxLength={128} required value={password}
            onChange={(event) => setPassword(event.target.value)} />
          {mode === "login" && <Link className={styles.passwordResetLink} href="/recuperar-senha">Esqueci minha senha</Link>}
          {mode === "register" && <p className={styles.hint}>Use de 12 a 128 caracteres. Espaços e Unicode são aceitos.</p>}
          <button className={styles.submit} type="submit" disabled={busy}>
            {busy ? "Aguarde…" : mode === "login" ? "Entrar com segurança" : "Concluir cadastro"}
          </button>
        </form>

        {onOAuthLogin && (
          <div className={styles.oauth}>
            <p className={styles.oauthDivider}>ou</p>
            <button type="button" onClick={() => onOAuthLogin("google")}>Continuar com Google</button>
            <button type="button" onClick={() => onOAuthLogin("github")}>Continuar com GitHub</button>
          </div>
        )}
      </section>
    </main>
  );
}
