"use client";

import { useEffect, type ComponentType } from "react";
import Link from "next/link";
import { ThemeSelector } from "../../theme/ThemeSelector";
import { BrandMark } from "../ui/BrandMark";
import {
  CalendarIcon,
  ChartBarIcon,
  GearIcon,
  HomeIcon,
  PlusIcon,
  ReceiptIcon,
  TargetIcon,
  TrendUpIcon,
  WalletIcon,
} from "../ui/NavIcons";
import styles from "./AppShell.module.css";

type Destination = "overview" | "envelopes" | "goals" | "annual" | "expenses" | "reports" | "carteira" | "settings";

type NavItem = {
  id: Destination;
  href: string;
  label: string;
  monthAware?: boolean;
  Icon: ComponentType<{ className?: string }>;
};

const budgetGroups: Array<{ label: string; items: NavItem[] }> = [
  {
    label: "Dia a dia",
    items: [
      { id: "overview", href: "/", label: "Visão geral", monthAware: true, Icon: HomeIcon },
      { id: "envelopes", href: "/verbas", label: "Verbas", monthAware: true, Icon: WalletIcon },
      { id: "expenses", href: "/gastos", label: "Histórico", Icon: ReceiptIcon },
    ],
  },
  {
    label: "Planejamento",
    items: [
      { id: "goals", href: "/metas", label: "Metas", Icon: TargetIcon },
      { id: "annual", href: "/gastos-anuais", label: "Gastos anuais", Icon: CalendarIcon },
      { id: "reports", href: "/relatorios", label: "Relatórios", Icon: ChartBarIcon },
    ],
  },
  {
    label: "Conta",
    items: [
      { id: "settings", href: "/configuracoes", label: "Configurações", Icon: GearIcon },
    ],
  },
];

const wealthGroups: Array<{ label: string; items: NavItem[] }> = [
  {
    label: "Patrimônio",
    items: [
      { id: "carteira", href: "/carteira", label: "Carteira", Icon: TrendUpIcon },
    ],
  },
  {
    label: "Conta",
    items: [
      { id: "settings", href: "/configuracoes", label: "Configurações", Icon: GearIcon },
    ],
  },
];

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

export function AppShell({
  current,
  email,
  month,
  onLogout,
  onQuickExpense,
  children,
}: {
  current: Destination;
  email: string;
  month?: string;
  onLogout(): void;
  onQuickExpense?: () => void;
  children: React.ReactNode;
}) {
  const isWealthArea = current === "carteira";
  const groups = isWealthArea ? wealthGroups : budgetGroups;

  useEffect(() => {
    if (!onQuickExpense || isWealthArea) return;
    function onKeyDown(event: KeyboardEvent) {
      if (isTypingTarget(event.target)) return;
      if (event.key.toLowerCase() !== "n" || event.altKey) return;
      const withModifier = event.metaKey || event.ctrlKey;
      const plain = !event.metaKey && !event.ctrlKey;
      if (!withModifier && !plain) return;
      event.preventDefault();
      onQuickExpense?.();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onQuickExpense, isWealthArea]);

  function resolveHref(item: NavItem) {
    if (!month || !item.monthAware) return item.href;
    return `${item.href}?month=${month}`;
  }

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#conteudo">Ir para o conteúdo</a>
      <aside className={styles.sidebar}>
        <Link className={styles.brand} href="/" aria-label="Verbas, visão geral">
          <span className={styles.brandMark} aria-hidden="true">
            <BrandMark size={22} />
          </span>
          <span><strong>Verbas</strong><small>painel financeiro</small></span>
        </Link>

        <div className={styles.areaSwitcher} role="group" aria-label="Área do aplicativo">
          <Link href="/" aria-current={!isWealthArea ? "true" : undefined}>Orçamento</Link>
          <Link href="/carteira" aria-current={isWealthArea ? "true" : undefined}>Patrimônio</Link>
        </div>

        {onQuickExpense && !isWealthArea && (
          <button
            type="button"
            className={styles.quickAction}
            onClick={onQuickExpense}
          >
            <PlusIcon className={styles.quickActionIcon} />
            <span>Registrar gasto</span>
          </button>
        )}

        <nav className={styles.nav} aria-label="Navegação principal">
          {groups.map((group) => (
            <div key={group.label} className={styles.navGroup}>
              <span className={styles.navGroupLabel} aria-hidden="true">{group.label}</span>
              <ul className={styles.navList}>
                {group.items.map((item) => {
                  const { id, label, Icon } = item;
                  return (
                    <li key={id}>
                      <Link href={resolveHref(item)} aria-current={current === id ? "page" : undefined}>
                        <Icon className={styles.navIcon} />
                        <span>{label}</span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        <div className={styles.sidebarFooter}>
          <ThemeSelector />
          <div className={styles.account}>
            <span className={styles.avatar} aria-hidden="true">{email.slice(0, 1).toUpperCase()}</span>
            <span>
              <strong>{email}</strong>
              <button type="button" onClick={onLogout}>Sair da conta</button>
            </span>
          </div>
        </div>
      </aside>
      <main id="conteudo" className={styles.main}>{children}</main>
    </div>
  );
}
