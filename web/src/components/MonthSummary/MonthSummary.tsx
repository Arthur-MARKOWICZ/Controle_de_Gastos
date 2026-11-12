import { formatBRL } from "../../lib/money";
import styles from "../Dashboard/Dashboard.module.css";
import { Button } from "../ui/Button";
import type { IncomeDTO, LedgerSummaryDTO } from "../../lib/api";

type Props = {
  summary: LedgerSummaryDTO | null;
  income: IncomeDTO | null;
  month: string;
  monthLabel: string;
  loading?: boolean;
  error?: string | null;
  /** Renda da própria sessão; participante não configura a renda de outro usuário. */
  onConfigureIncome?: () => void;
};

/**
 * Resumo único do mês: renda, já reservado, não alocado e uso.
 * A renda aparece aqui uma só vez — nenhum outro bloco da Visão geral
 * repete esse valor como figura principal.
 */
export function MonthSummary({ summary, income, month, monthLabel, loading, error, onConfigureIncome }: Props) {
  const effectiveIncome = summary?.income ?? income;
  const incomeText = effectiveIncome ? formatBRL(effectiveIncome.amount) : "Renda não configurada";
  const vigency = effectiveIncome
    ? effectiveIncome.effectiveFrom === month
      ? `Vigência ${effectiveIncome.effectiveFrom}`
      : `Vigência ${effectiveIncome.effectiveFrom} → vale para ${month}`
    : `${monthLabel} — sem renda efetiva`;

  const actionLabel = effectiveIncome ? "Editar renda" : "Configurar renda";

  return (
    <section
      id="visao-geral"
      data-summary=""
      className={styles.summary}
      aria-labelledby="resumo-titulo"
      aria-busy={loading || undefined}
    >
      <div className={styles.summaryLead}>
        <div className={styles.incomeHeader}>
          <h2 id="resumo-titulo">Resumo do mês</h2>
          {onConfigureIncome && (
            <Button variant="text" className={styles.editButton} onClick={onConfigureIncome}>
              {actionLabel}
            </Button>
          )}
        </div>
        <strong>{loading ? "Carregando…" : incomeText}</strong>
        <span {...(error ? { role: "alert" } : {})}>{error ?? vigency}</span>
        {effectiveIncome && (
          <span className={styles.incomeMeta}>
            Atualizado em {new Date(effectiveIncome.changedAt).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })}
          </span>
        )}
      </div>
      <dl className={styles.summaryStats}>
        <div><dt>Já reservado</dt><dd>{summary ? formatBRL(summary.allocated.amount) : "—"}</dd></div>
        <div><dt>Não alocado</dt><dd>{summary ? formatBRL(summary.unallocated.amount) : "—"}</dd></div>
        <div><dt>Uso da renda</dt><dd>{summary?.income ? `${Math.round(summary.usagePct)}%` : "—"}</dd></div>
      </dl>
    </section>
  );
}
