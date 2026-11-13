"use client";

import Link from "next/link";
import { AppShell } from "../AppShell/AppShell";
import { PageHeader } from "../ui/PageHeader";
import styles from "./SettingsPage.module.css";

export function SettingsPage({ email, onLogout }: { email: string; onLogout(): void }) {
  return (
    <AppShell current="settings" email={email} onLogout={onLogout}>
      <PageHeader
        eyebrow="Conta"
        title="Configurações"
        description="Preferências da sua conta e do painel."
      />
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
        <li>
          <Link className={styles.item} href="/privacidade">
            <span className={styles.itemLabel}>
              <strong>Política de privacidade</strong>
              <span>Finalidades do tratamento e canal do titular.</span>
            </span>
            <span className={styles.chevron} aria-hidden="true">›</span>
          </Link>
        </li>
      </ul>
    </AppShell>
  );
}
