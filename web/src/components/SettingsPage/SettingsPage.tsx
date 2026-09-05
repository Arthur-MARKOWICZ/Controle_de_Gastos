"use client";

import Link from "next/link";
import { AppShell } from "../AppShell/AppShell";
import pageStyles from "../VerbasPage/Verbas.module.css";
import styles from "./SettingsPage.module.css";

export function SettingsPage({ email, onLogout }: { email: string; onLogout(): void }) {
  return (
    <AppShell current="settings" email={email} onLogout={onLogout}>
      <header className={pageStyles.pageHeader}>
        <div>
          <p className={pageStyles.eyebrow}>Configurações</p>
          <h1>Configurações</h1>
          <p>Preferências da sua conta e do painel.</p>
        </div>
      </header>
      <ul className={styles.list}>
        <li>
          <Link className={styles.item} href="/conta/seguranca">
            <span className={styles.itemLabel}>
              <strong>Segurança</strong>
              <span>Autenticação em duas etapas (MFA) e recovery codes.</span>
            </span>
            <span className={styles.chevron} aria-hidden="true">›</span>
          </Link>
        </li>
      </ul>
    </AppShell>
  );
}
