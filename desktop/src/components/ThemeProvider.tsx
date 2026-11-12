import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { themeGet, themeSet } from "../lib/ipc";
import type { ThemeMode } from "../lib/ipc";

/**
 * Tema do dispositivo (ADR-013, emendado pelo ADR-0029).
 *
 * A escolha vive num arquivo local gravado pelo processo Rust. Nenhuma chamada
 * à API: o tema não é dado de conta.
 */

type Resolved = "light" | "dark";

type ThemeContextValue = {
  mode: ThemeMode;
  resolved: Resolved;
  setMode: (mode: ThemeMode) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

const DARK_QUERY = "(prefers-color-scheme: dark)";

function systemPrefersDark(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia(DARK_QUERY).matches;
}

export function resolveTheme(mode: ThemeMode, prefersDark: boolean): Resolved {
  if (mode === "DARK") return "dark";
  if (mode === "LIGHT") return "light";
  return prefersDark ? "dark" : "light";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>("SYSTEM");
  const [prefersDark, setPrefersDark] = useState<boolean>(() => systemPrefersDark());

  // Escolha guardada no dispositivo. Falha de leitura não derruba a interface:
  // o tema simplesmente segue o sistema.
  useEffect(() => {
    let active = true;
    themeGet()
      .then((stored) => {
        if (active) setModeState(stored);
      })
      .catch(() => {
        /* sem preferência utilizável: segue o sistema */
      });
    return () => {
      active = false;
    };
  }, []);

  // Enquanto o modo é SYSTEM, acompanha a troca no sistema operacional.
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const query = window.matchMedia(DARK_QUERY);
    const listener = (event: MediaQueryListEvent) => setPrefersDark(event.matches);
    query.addEventListener("change", listener);
    setPrefersDark(query.matches);
    return () => query.removeEventListener("change", listener);
  }, []);

  const resolved = resolveTheme(mode, prefersDark);

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = resolved;
    root.dataset.themePreference = mode;
    root.style.colorScheme = resolved;
  }, [mode, resolved]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    void themeSet(next).catch(() => {
      /* não gravou no dispositivo: a escolha vale para esta janela */
    });
  }, []);

  return (
    <ThemeContext.Provider value={{ mode, resolved, setMode }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const value = useContext(ThemeContext);
  if (!value) {
    throw new Error("useTheme exige ThemeProvider acima na árvore");
  }
  return value;
}
