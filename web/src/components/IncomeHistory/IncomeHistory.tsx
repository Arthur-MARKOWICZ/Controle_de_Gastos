import { formatBRL } from "../../lib/money";
import styles from "../Dashboard/Dashboard.module.css";
import type { IncomeHistoryPageDTO } from "../../lib/api";

type Props = {
  data: IncomeHistoryPageDTO | null;
  loading: boolean;
  error: string | null;
  onLoadMore?(): void;
  onRetry?(): void;
};

export function IncomeHistory({ data, loading, error, onLoadMore, onRetry }: Props) {
  if (loading && !data) {
    return <p role="status" aria-busy="true" style={{ padding: "1rem 1.5rem", color: "var(--foreground-muted)" }}>Carregando histórico…</p>;
  }
  if (error) {
    return <p role="alert" style={{ padding: "1rem 1.5rem", color: "#7a2a2a" }}>{error} {onRetry && <button type="button" onClick={onRetry} style={{ marginLeft: "0.5rem", color: "var(--accent)", fontWeight: 700, border: 0, background: "transparent", cursor: "pointer" }}>Tentar novamente</button>}</p>;
  }
  if (!data || data.items.length === 0) {
    return <p style={{ padding: "1rem 1.5rem", color: "var(--foreground-muted)", fontSize: "0.8125rem" }}>Nenhuma alteração registrada — a primeira configuração criará o histórico.</p>;
  }
  return (
    <div>
      <ul className={styles.activityList} role="list" aria-label="Histórico de renda">
        {data.items.map((item) => (
          <li key={item.id}>
            <span className={styles.activityMark} aria-hidden="true" />
            <div>
              <strong>{formatBRL(item.amount)}</strong>
              <span>Vigência {item.effectiveFrom} · {new Date(item.changedAt).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })}</span>
            </div>
            <span className={styles.activityAmount}>{item.currency}</span>
          </li>
        ))}
      </ul>
      {data.hasNext && onLoadMore && (
        <button type="button" onClick={onLoadMore} className={styles.secondaryAction} style={{ margin: "1rem 1.5rem" }}>
          Carregar mais
        </button>
      )}
    </div>
  );
}
