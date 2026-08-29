import { formatBRL } from "../../lib/money";
import styles from "../Dashboard/Dashboard.module.css";
import type { LedgerSummaryDTO } from "../../lib/api";

export function BalanceSummary({ summary, monthLabel }: { summary: LedgerSummaryDTO | null; monthLabel: string }) {
  if (!summary) {
    return (
      <section className={styles.summary} aria-labelledby="resumo-titulo">
        <div className={styles.summaryLead}><h2 id="resumo-titulo">Renda do mês</h2><strong>—</strong><span>{monthLabel}</span></div>
        <dl className={styles.summaryStats}><div><dt>Já reservado</dt><dd>—</dd></div><div><dt>Não alocado</dt><dd>—</dd></div><div><dt>Uso da renda</dt><dd>—</dd></div></dl>
      </section>
    );
  }
  const incomeText = summary.income ? formatBRL(summary.income.amount) : "Renda não configurada";
  const allocated = formatBRL(summary.allocated.amount);
  const unallocated = formatBRL(summary.unallocated.amount);
  const pct = Math.round(summary.usagePct);

  return (
    <section id="visao-geral" className={styles.summary} aria-labelledby="resumo-titulo">
      <div className={styles.summaryLead}>
        <h2 id="resumo-titulo">Renda do mês</h2>
        <strong>{incomeText}</strong>
        <span>{summary.income ? `Vigência ${summary.income.effectiveFrom}` : monthLabel}</span>
      </div>
      <dl className={styles.summaryStats}>
        <div><dt>Já reservado</dt><dd>{allocated}</dd></div>
        <div><dt>Não alocado</dt><dd>{unallocated}</dd></div>
        <div><dt>Uso da renda</dt><dd>{summary.income ? `${pct}%` : "—"}</dd></div>
      </dl>
    </section>
  );
}
