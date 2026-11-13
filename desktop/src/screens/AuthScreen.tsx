import { useRef, useState } from "react";
import { Field } from "../components/Field";
import { IpcError, login, verifyMfa } from "../lib/ipc";
import type { AuthReply, SessionView } from "../lib/ipc";
import styles from "./AuthScreen.module.css";

/** Mensagem única para credencial inválida: não revela se a conta existe. */
const CREDENCIAL_INVALIDA = "E-mail ou senha incorretos.";
const CODIGO_INVALIDO = "Código inválido ou expirado. Tente o código atual do aplicativo.";

type Phase =
  | { kind: "credentials" }
  | { kind: "mfa"; challengeId: string };

export type AuthScreenProps = {
  onAuthenticated: (session: SessionView) => void;
};

export function AuthScreen({ onAuthenticated }: AuthScreenProps) {
  const [phase, setPhase] = useState<Phase>({ kind: "credentials" });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const alertRef = useRef<HTMLParagraphElement>(null);

  function fail(message: string) {
    setError(message);
    // O erro recebe foco para quem usa leitor de tela não perder o aviso.
    requestAnimationFrame(() => alertRef.current?.focus());
  }

  function handleReply(reply: AuthReply) {
    if (reply.status === "authenticated") {
      onAuthenticated(reply.session);
      return;
    }
    setPhase({ kind: "mfa", challengeId: reply.challengeId });
    setCode("");
    setError(null);
  }

  async function submitCredentials(event: React.FormEvent) {
    event.preventDefault();
    if (pending) return;
    setError(null);
    if (email.trim() === "" || password === "") {
      fail("Informe e-mail e senha.");
      return;
    }
    setPending(true);
    try {
      handleReply(await login(email.trim(), password));
    } catch (raw) {
      const ipcError = raw as IpcError;
      // Qualquer recusa de credencial mostra a mesma frase.
      fail(ipcError.message || CREDENCIAL_INVALIDA);
    } finally {
      setPending(false);
      setPassword("");
    }
  }

  async function submitCode(event: React.FormEvent) {
    event.preventDefault();
    if (pending || phase.kind !== "mfa") return;
    setError(null);
    if (code.trim() === "") {
      fail("Informe o código do aplicativo autenticador.");
      return;
    }
    setPending(true);
    try {
      handleReply(await verifyMfa(phase.challengeId, code.trim()));
    } catch (raw) {
      const ipcError = raw as IpcError;
      // Permanece no desafio: a sessão não foi emitida.
      fail(ipcError.message || CODIGO_INVALIDO);
    } finally {
      setPending(false);
    }
  }

  return (
    <main className={styles.screen} id="conteudo">
      <div className={styles.card}>
        <p className={styles.brand}>Verbas</p>

        {error ? (
          <p className={styles.alert} role="alert" tabIndex={-1} ref={alertRef}>
            {error}
          </p>
        ) : null}

        {phase.kind === "credentials" ? (
          <>
            <h1 className={styles.title}>Entrar</h1>
            <p className={styles.lead}>
              Use o mesmo e-mail e senha da conta. Criar conta, recuperar senha e
              login social continuam na web.
            </p>
            <form className={styles.form} onSubmit={submitCredentials} noValidate>
              <Field
                id="email"
                label="E-mail"
                type="email"
                value={email}
                onChange={setEmail}
                autoComplete="username"
                inputMode="email"
                autoFocus
                disabled={pending}
              />
              <Field
                id="senha"
                label="Senha"
                type="password"
                value={password}
                onChange={setPassword}
                autoComplete="current-password"
                disabled={pending}
              />
              <button className={styles.submit} type="submit" disabled={pending}>
                {pending ? "Entrando…" : "Entrar"}
              </button>
            </form>
          </>
        ) : (
          <>
            <h1 className={styles.title}>Verificação em duas etapas</h1>
            <p className={styles.lead}>
              Informe o código de seis dígitos do seu aplicativo autenticador.
            </p>
            <form className={styles.form} onSubmit={submitCode} noValidate>
              <Field
                id="codigo"
                label="Código do autenticador"
                value={code}
                onChange={setCode}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={8}
                autoFocus
                disabled={pending}
              />
              <button className={styles.submit} type="submit" disabled={pending}>
                {pending ? "Verificando…" : "Verificar"}
              </button>
            </form>
            <button
              className={styles.secondary}
              type="button"
              onClick={() => {
                setPhase({ kind: "credentials" });
                setCode("");
                setError(null);
              }}
            >
              Voltar e entrar com outra conta
            </button>
          </>
        )}
      </div>
    </main>
  );
}
