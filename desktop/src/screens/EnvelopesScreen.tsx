import { Plus, WarningCircle } from "@phosphor-icons/react";
import { Amount } from "../components/Amount";
import { MonthSelector } from "../components/MonthSelector";
import { useExpenseCapture } from "../components/useExpenseCapture";
import { useSummary } from "../lib/use-summary";
import { isSpendable, PURPOSE_LABELS } from "../lib/types";
import type { ScreenProps } from "./OverviewScreen";
import styles from "./Screens.module.css";

/**
 * Lista densa das verbas visíveis no mês.
 *
 * A fonte é o campo `envelopes` de `GET /api/v1/ledger/summary`, **não**
 * `GET /api/v1/envelopes`: naquele contrato `available` espelha `baseAmount` e
 * `isNegative` é sempre `false`, o que esconderia verba negativa (ADR-0029).
 */
export function EnvelopesScreen({
  month,
  onMonthChange,
  revision,
  onRegistered,
  onAuthRequired,
}: ScreenProps) {
  const { state } = useSummary(month, revision, onAuthRequired);
  const envelopes = state.kind === "ready" ? state.summary.envelopes : [];
  const { openFor, dialog } = useExpenseCapture(envelopes, onRegistered);

  return (
    <div className={styles.screen}>
      <header className={styles.header}>
        <h1 className={styles.title}>Verbas</h1>
        <MonthSelector month={month} onChange={onMonthChange} />
      </header>

      {state.kind === "loading" ? <p role="status">Carregando as verbas…</p> : null}
      {state.kind === "error" ? (
        <p className={styles.error} role="alert">
          {state.message}
        </p>
      ) : null}

      {state.kind === "ready" && envelopes.length === 0 ? (
        <p className={styles.empty}>
          Nenhuma verba neste mês. Crie verbas na web para começar a registrar
          gastos aqui.
        </p>
      ) : null}

      {envelopes.length > 0 ? (
        <ul className={styles.list} aria-label="Verbas do mês">
          {envelopes.map((envelope) => (
            <li className={styles.row} key={envelope.id} data-envelope={envelope.name}>
              <div className={styles.rowMain}>
                <span className={styles.rowName}>{envelope.name}</span>
                <span className={styles.rowPurpose}>
                  {PURPOSE_LABELS[envelope.purpose]}
                  {envelope.role === "PARTICIPANT" ? " · compartilhada" : ""}
                </span>
              </div>

              <div>
                <Amount
                  className={`${styles.rowAmount} ${
                    envelope.isNegative ? styles.rowAmountNegative : ""
                  }`}
                  plain={envelope.available.amount}
                />
                {/* Estado negativo em texto e ícone, não só na cor. */}
                {envelope.isNegative ? (
                  <span className={styles.negativeFlag}>
                    <WarningCircle size={20} aria-hidden />
                    Saldo negativo
                  </span>
                ) : null}
              </div>

              {isSpendable(envelope) ? (
                <button
                  className={styles.rowAction}
                  type="button"
                  onClick={() => openFor(envelope)}
                >
                  <Plus size={20} aria-hidden />
                  Registrar gasto
                </button>
              ) : (
                <span className={styles.rowPurpose}>Arquivada</span>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      {dialog}
    </div>
  );
}
