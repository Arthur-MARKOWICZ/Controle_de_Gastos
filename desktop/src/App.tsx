import { useCallback, useEffect, useState } from "react";
import { AppShell } from "./components/AppShell";
import type { ScreenId } from "./components/AppShell";
import { ThemeProvider } from "./components/ThemeProvider";
import { AuthScreen } from "./screens/AuthScreen";
import { EnvelopesScreen } from "./screens/EnvelopesScreen";
import { OverviewScreen } from "./screens/OverviewScreen";
import { logout as logoutCommand, reminderNotify, restore } from "./lib/ipc";
import { getNotificationPreferences, hasExpenseToday } from "./lib/api";
import { useDailyReminder } from "./lib/use-daily-reminder";
import { ReminderDialog } from "./components/ReminderDialog";
import type { NotificationPreferences } from "./lib/types";
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
    <SignedIn
      email={status.session.email}
      screen={screen}
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
    </SignedIn>
  );
}

const REMINDER_DEPS = { hasExpenseToday, notify: reminderNotify };

/**
 * Janela autenticada. O lembrete diário (ADR-0033) vive aqui: sair da conta
 * desmonta este componente e cancela o agendamento.
 */
function SignedIn({
  email,
  screen,
  onNavigate,
  onLogout,
  children,
}: {
  email: string;
  screen: ScreenId;
  onNavigate: (screen: ScreenId) => void;
  onLogout: () => void;
  children: React.ReactNode;
}) {
  const [reminder, setReminder] = useState<NotificationPreferences | null>(null);
  const [reminderOpen, setReminderOpen] = useState(false);

  useEffect(() => {
    let active = true;
    getNotificationPreferences()
      .then((preferences) => active && setReminder(preferences))
      .catch(() => {
        /* sem preferências, nenhum lembrete é agendado */
      });
    return () => {
      active = false;
    };
  }, []);

  useDailyReminder(
    { enabled: reminder?.dailyReminder === true, time: reminder?.reminderTime ?? "20:00" },
    REMINDER_DEPS,
  );

  return (
    <AppShell
      email={email}
      current={screen}
      onNavigate={onNavigate}
      onLogout={onLogout}
      onOpenReminder={() => setReminderOpen(true)}
    >
      {children}
      {reminderOpen ? (
        <ReminderDialog onClose={() => setReminderOpen(false)} onSaved={setReminder} />
      ) : null}
    </AppShell>
  );
}
