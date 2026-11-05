import { ApiError, type ControleGastosApiClient } from "./api-client.js";

export type AskFn = (prompt: string, options?: { secret?: boolean }) => Promise<string>;

export type LoginFlowDeps = {
  client: ControleGastosApiClient;
  ask: AskFn;
  writeLine: (line: string) => void;
};

export type LoginFlowResult =
  | { ok: true; expiresIn: number }
  | { ok: false; message: string };

/**
 * Fluxo interativo de login para CLI local.
 * Não ecoa senha nem tokens nas mensagens.
 */
export async function runInteractiveLogin(
  deps: LoginFlowDeps,
): Promise<LoginFlowResult> {
  const email = (await deps.ask("E-mail: ")).trim();
  if (!email) {
    const message = "E-mail obrigatório.";
    deps.writeLine(message);
    return { ok: false, message };
  }

  const password = await deps.ask("Senha: ", { secret: true });
  if (!password) {
    const message = "Senha obrigatória.";
    deps.writeLine(message);
    return { ok: false, message };
  }

  try {
    const loginResult = await deps.client.login(email, password);
    if ("mfaRequired" in loginResult && loginResult.mfaRequired) {
      deps.writeLine("MFA necessário. Informe o código do autenticador.");
      const code = (await deps.ask("Código TOTP: ")).trim();
      if (!code) {
        const message = "Código MFA obrigatório.";
        deps.writeLine(message);
        return { ok: false, message };
      }
      const verified = await deps.client.verifyMfa(loginResult.challengeId, code);
      deps.writeLine(
        `Sessão salva. Access expira em ~${verified.expiresIn}s. Pode voltar ao chat.`,
      );
      return { ok: true, expiresIn: verified.expiresIn };
    }

    deps.writeLine(
      `Sessão salva. Access expira em ~${loginResult.expiresIn}s. Pode voltar ao chat.`,
    );
    return { ok: true, expiresIn: loginResult.expiresIn };
  } catch (error) {
    const message = formatLoginError(error);
    deps.writeLine(message);
    return { ok: false, message };
  }
}

function formatLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    return `Falha no login (HTTP ${error.status}). Verifique e-mail/senha ou MFA.`;
  }
  if (error instanceof Error) {
    return `Falha no login: ${error.message}`;
  }
  return "Falha no login.";
}
