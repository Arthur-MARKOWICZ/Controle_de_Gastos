"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  applyTheme,
  DARK_THEME_QUERY,
  isThemePreference,
  resolveTheme,
  THEME_STORAGE_KEY,
  type ResolvedTheme,
  type ThemePreference,
} from "./theme";

type ThemeContextValue = {
  preference: ThemePreference;
  resolvedTheme: ResolvedTheme;
  setPreference(preference: ThemePreference): void;
};

const ThemeContext = createContext<ThemeContextValue>({
  preference: "system",
  resolvedTheme: "light",
  setPreference: () => undefined,
});

function readInitialPreference(): ThemePreference {
  if (typeof document !== "undefined") {
    const bootstrapped = document.documentElement.dataset.themePreference ?? null;
    if (isThemePreference(bootstrapped)) return bootstrapped;
  }
  if (typeof localStorage !== "undefined") {
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY);
      if (isThemePreference(stored)) return stored;
    } catch {
      // Storage can be unavailable in hardened browsing contexts; the theme still works in memory.
    }
  }
  return "system";
}

function systemPrefersDark(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function" && window.matchMedia(DARK_THEME_QUERY).matches;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [preference, updatePreference] = useState<ThemePreference>(readInitialPreference);
  const [systemDark, setSystemDark] = useState(systemPrefersDark);
  const resolvedTheme: ResolvedTheme = resolveTheme(preference, systemDark);

  const setPreference = useCallback((next: ThemePreference) => {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // Keep the current tab usable even when the browser blocks local persistence.
    }
    updatePreference(next);
  }, []);

  useEffect(() => {
    applyTheme(preference, systemDark);
  }, [preference, systemDark]);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const media = window.matchMedia(DARK_THEME_QUERY);
    const onSystemThemeChange = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    media.addEventListener("change", onSystemThemeChange);
    return () => media.removeEventListener("change", onSystemThemeChange);
  }, []);

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== THEME_STORAGE_KEY) return;
      updatePreference(isThemePreference(event.newValue) ? event.newValue : "system");
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const value = useMemo(() => ({ preference, resolvedTheme, setPreference }), [preference, resolvedTheme, setPreference]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
