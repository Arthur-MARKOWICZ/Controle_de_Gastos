import { formatBRL } from "../../lib/money";
import { formatMonthLabel } from "../../lib/dates";
import styles from "../Dashboard/Dashboard.module.css";
import type { IncomeDTO } from "../../lib/api";

type Props = {
  income: IncomeDTO | null;
  month: string;
  loading?: boolean;
  error?: string | null;
  onEdit?: () => void;
};

export function IncomeCard({ income, month, loading, error, onEdit }: Props) {
  if (loading) {
    return (
      <section className={styles.summary} aria-labelledby="renda-titulo" aria-busy="true">
        <div className={styles.summaryLead}><h2 id="renda-titulo">Renda do mês</h2><strong>Carregando…</strong><span role="status">Buscando renda efetiva</span></div>
        <dl className={styles.summaryStats}><div><dt>Já reservado</dt><dd>—</dd></div><div><dt>Não alocado</dt><dd>—</dd></div><div><dt>Uso da renda</dt><dd>—</dd></div></dl>
      </section>
    );
  }
  if (error) {
    return (
      <section className={styles.summary} aria-labelledby="renda-titulo">
        <div className={styles.summaryLead}><h2 id="renda-titulo">Renda do mês</h2><strong>Erro</strong><span role="alert">{error}</span></div>
        <dl className={styles.summaryStats}><div><dt>Vigência</dt><dd>{formatMonthLabel(month)}</dd></div></dl>
      </section>
    );
  }
  if (!income) {
    return (
      <section className={styles.summary} aria-labelledby="renda-titulo">
        <div className={styles.summaryLead}><h2 id="renda-titulo">Renda do mês</h2><strong>Renda não configurada</strong><span>{formatMonthLabel(month)} — sem renda efetiva</span></div>
        <dl className={styles.summaryStats}>
          <div><dt>Vigência solicitada</dt><dd>{month}</dd></div>
          <div><dt>Ação</dt><dd>{onEdit ? <button type="button" onClick={onEdit} style={{ color: "var(--accent)", fontWeight: 700, border: 0, background: "transparent", cursor: "pointer" }}>Configurar renda</button> : "—"}</dd></div>
        </dl>
      </section>
    );
  }
  const isCurrentMonth = income.effectiveFrom === month;
  const vigencyLabel = isCurrentMonth ? `Vigência ${income.effectiveFrom}` : `Vigência ${income.effectiveFrom} → vale para ${month}`;
  return (
    <section className={styles.summary} aria-labelledby="renda-titulo" style={{ position: "relative" }}>
      <div className={styles.summaryLead}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "0.5rem" }}>
          <h2 id="renda-titulo">Renda do mês</h2>
          {onEdit && <button type="button" onClick={onEdit} aria-label="Editar renda" style={{ fontSize: "0.8125rem", color: "var(--accent)", fontWeight: 700, border: "1px solid var(--border)", background: "var(--surface)", borderRadius: "0.375rem", padding: "0.25rem 0.5rem", cursor: "pointer" }}>Editar</button>}
        </div>
        <strong>{formatBRL(income.amount)}</strong>
        <span>{vigencyLabel}</span>
        <span style={{ fontSize: "0.75rem", color: "var(--foreground-muted)" }}>Atualizado em {new Date(income.changedAt).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })}</span>
      </div>
      <dl className={styles.summaryStats}>
        <div><dt>Valor</dt><dd>{formatBRL(income.amount)}</dd></div>
        <div><dt>Moeda</dt><dd>{income.currency}</dd></div>
        <div><dt>Vigência</dt><dd>{income.effectiveFrom}</dd></div>
      </dl>
    </section>
  );
}
