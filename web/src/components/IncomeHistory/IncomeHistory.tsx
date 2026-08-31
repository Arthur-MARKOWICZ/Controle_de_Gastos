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
    return <p role="status" aria-busy="true" className={styles.listState}>Carregando histórico…</p>;
  }
  if (error) {
    return <p role="alert" className={styles.listError}>{error} {onRetry && <button type="button" onClick={onRetry} className={styles.textAction}>Tentar novamente</button>}</p>;
  }
  if (!data || data.items.length === 0) {
    return <p className={styles.listState}>Nenhuma alteração registrada — a primeira configuração criará o histórico.</p>;
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
        <button type="button" onClick={onLoadMore} className={`${styles.secondaryAction} ${styles.loadMore}`}>
          Carregar mais
        </button>
      )}
    </div>
  );
}
