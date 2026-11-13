import { useCallback, useEffect, useState } from "react";
import { AppShell } from "./components/AppShell";
import type { ScreenId } from "./components/AppShell";
import { ThemeProvider } from "./components/ThemeProvider";
import { AuthScreen } from "./screens/AuthScreen";
import { EnvelopesScreen } from "./screens/EnvelopesScreen";
import { OverviewScreen } from "./screens/OverviewScreen";
import { logout as logoutCommand, restore } from "./lib/ipc";
import type { SessionView } from "./lib/ipc";
import { currentMonthSaoPaulo } from "./lib/dates";

type Status =
  | { kind: "starting" }
  | { kind: "anonymous"; notice?: string }
  | { kind: "authenticated"; session: SessionView };

export function App() {
  return (
    <ThemeProvider>
      <AuthenticatedApp />
    </ThemeProvider>
  );
}

function AuthenticatedApp() {
  const [status, setStatus] = useState<Status>({ kind: "starting" });
  const [screen, setScreen] = useState<ScreenId>("overview");
  const [month, setMonth] = useState(() => currentMonthSaoPaulo());
  /** Muda a cada lançamento para as telas recarregarem o resumo. */
  const [revision, setRevision] = useState(0);

  // Arranque: refresh válido no dispositivo restaura a sessão sem senha.
  useEffect(() => {
    let active = true;
    restore()
      .then((reply) => {
        if (!active) return;
        setStatus(
          reply.session
            ? { kind: "authenticated", session: reply.session }
            : { kind: "anonymous" },
        );
      })
      .catch((error: { message?: string }) => {
        if (!active) return;
        // Cofre indisponível, por exemplo: a mensagem precisa aparecer.
        setStatus({ kind: "anonymous", notice: error?.message });
      });
    return () => {
      active = false;
    };
  }, []);

  const handleLogout = useCallback(() => {
    void logoutCommand()
      .catch(() => {
        /* a sessão local já foi descartada pelo processo nativo */
      })
      .finally(() => {
        setStatus({ kind: "anonymous" });
        setScreen("overview");
      });
  }, []);

  const handleAuthRequired = useCallback(() => {
    setStatus({ kind: "anonymous", notice: "Sessão expirada. Entre de novo." });
  }, []);

  const handleRegistered = useCallback(() => setRevision((n) => n + 1), []);

  if (status.kind === "starting") {
    return (
      <main id="conteudo">
        <p role="status">Abrindo sua sessão…</p>
      </main>
    );
  }

  if (status.kind === "anonymous") {
    return (
      <>
        {status.notice ? <p role="status">{status.notice}</p> : null}
        <AuthScreen
          onAuthenticated={(session) => setStatus({ kind: "authenticated", session })}
        />
      </>
    );
  }

  return (
    <AppShell
      email={status.session.email}
      current={screen}
      onNavigate={setScreen}
      onLogout={handleLogout}
    >
      {screen === "overview" ? (
        <OverviewScreen
          month={month}
          onMonthChange={setMonth}
          revision={revision}
          onRegistered={handleRegistered}
          onAuthRequired={handleAuthRequired}
        />
      ) : (
        <EnvelopesScreen
          month={month}
          onMonthChange={setMonth}
          revision={revision}
          onRegistered={handleRegistered}
          onAuthRequired={handleAuthRequired}
        />
      )}
    </AppShell>
  );
}
