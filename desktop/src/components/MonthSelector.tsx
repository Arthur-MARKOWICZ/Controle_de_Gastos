import { formatMonthLabel, shiftMonth } from "../lib/dates";
import styles from "./MonthSelector.module.css";

/** Mês de referência em America/Sao_Paulo. Trocar recarrega o resumo. */
export function MonthSelector({
  month,
  onChange,
}: {
  month: string;
  onChange: (month: string) => void;
}) {
  return (
    <div className={styles.selector}>
      <button
        className={styles.step}
        type="button"
        aria-label="Mês anterior"
        onClick={() => onChange(shiftMonth(month, -1))}
      >
        ‹
      </button>
      <output className={styles.label} aria-live="polite" data-month={month}>
        {formatMonthLabel(month)}
      </output>
      <button
        className={styles.step}
        type="button"
        aria-label="Mês seguinte"
        onClick={() => onChange(shiftMonth(month, 1))}
      >
        ›
      </button>
    </div>
  );
}
