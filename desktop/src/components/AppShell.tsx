import { House, Wallet } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { useTheme } from "./ThemeProvider";
import type { ThemeMode } from "../lib/ipc";
import styles from "./AppShell.module.css";

/** Ícones Phosphor outline a 20px. `Envelope` não entra: é e-mail. */
const ICON_SIZE = 20;

export type ScreenId = "overview" | "envelopes";

const DESTINATIONS: { id: ScreenId; label: string; Icon: typeof House }[] = [
  { id: "overview", label: "Visão geral", Icon: House },
  { id: "envelopes", label: "Verbas", Icon: Wallet },
];

const THEME_OPTIONS: { value: ThemeMode; label: string }[] = [
  { value: "SYSTEM", label: "Seguir o sistema" },
  { value: "LIGHT", label: "Claro" },
  { value: "DARK", label: "Escuro" },
];

export type AppShellProps = {
  email: string;
  current: ScreenId;
  onNavigate: (screen: ScreenId) => void;
  onLogout: () => void;
  children: ReactNode;
};

export function AppShell({
  email,
  current,
  onNavigate,
  onLogout,
  children,
}: AppShellProps) {
  const { mode, setMode } = useTheme();

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#conteudo">
        Pular para o conteúdo
      </a>

      <div className={styles.sidebar}>
        {/* `data-tauri-drag-region` arrasta a janela sem decoração nativa. */}
        <div className={styles.titlebar} data-tauri-drag-region>
          Verbas
        </div>

        <nav className={styles.nav} aria-label="Navegação principal">
          {DESTINATIONS.map(({ id, label, Icon }) => (
            <button
              key={id}
              className={styles.navItem}
              type="button"
              aria-current={current === id ? "page" : undefined}
              onClick={() => onNavigate(id)}
            >
              <Icon className={styles.navIcon} size={ICON_SIZE} aria-hidden />
              {label}
            </button>
          ))}
        </nav>

        <div className={styles.spacer} />

        <div className={styles.footer}>
          <p className={styles.account}>{email}</p>

          <div className={styles.themeRow}>
            <label className={styles.themeLabel} htmlFor="tema">
              Tema
            </label>
            <select
              className={styles.themeSelect}
              id="tema"
              value={mode}
              onChange={(event) => setMode(event.target.value as ThemeMode)}
            >
              {THEME_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <button className={styles.logout} type="button" onClick={onLogout}>
            Sair
          </button>
        </div>
      </div>

      <main className={styles.main} id="conteudo" tabIndex={-1}>
        {children}
      </main>
    </div>
  );
}
