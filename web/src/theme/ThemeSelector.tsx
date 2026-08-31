"use client";

import { useTheme } from "./ThemeProvider";
import type { ThemePreference } from "./theme";
import styles from "./ThemeSelector.module.css";

export function ThemeSelector({ compact = false }: { compact?: boolean }) {
  const { preference, setPreference } = useTheme();
  return (
    <label className={styles.field}>
      {!compact && <span>Aparência</span>}
      <select aria-label="Tema" value={preference} onChange={(event) => setPreference(event.target.value as ThemePreference)}>
        <option value="system">Sistema</option>
        <option value="light">Claro</option>
        <option value="dark">Escuro</option>
      </select>
    </label>
  );
}
