"use client";

import Link from "next/link";
import { ThemeSelector } from "../../theme/ThemeSelector";
import styles from "./AppShell.module.css";

type Destination = "overview" | "envelopes" | "history" | "reports";

const destinations: Array<{ id: Destination; href: string; index: string; label: string }> = [
  { id: "overview", href: "/", index: "01", label: "Visão geral" },
  { id: "envelopes", href: "/verbas", index: "02", label: "Verbas" },
  { id: "history", href: "/historico", index: "03", label: "Histórico" },
  { id: "reports", href: "/relatorios", index: "04", label: "Relatórios" },
];

export function AppShell({ current, email, onLogout, children }: {
  current: Destination;
  email: string;
  onLogout(): void;
  children: React.ReactNode;
}) {
  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#conteudo">Ir para o conteúdo</a>
      <aside className={styles.sidebar}>
        <Link className={styles.brand} href="/" aria-label="Verbas, visão geral">
          <span className={styles.brandMark} aria-hidden="true">V</span>
          <span><strong>Verbas</strong><small>painel financeiro</small></span>
        </Link>
        <nav aria-label="Navegação principal">
          <ul className={styles.navList}>
            {destinations.map((destination) => (
              <li key={destination.id}>
                <Link href={destination.href} aria-current={current === destination.id ? "page" : undefined}>
                  <span aria-hidden="true">{destination.index}</span>{destination.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className={styles.sidebarFooter}>
          <ThemeSelector />
          <div className={styles.account}>
            <span className={styles.avatar} aria-hidden="true">{email.slice(0, 1).toUpperCase()}</span>
            <span><strong>{email}</strong><button type="button" onClick={onLogout}>Sair da conta</button></span>
          </div>
        </div>
      </aside>
      <main id="conteudo" className={styles.main}>{children}</main>
    </div>
  );
}
