import { useCallback, useEffect, useState } from "react";
import { Plus, WarningCircle } from "@phosphor-icons/react";
import { Amount } from "../components/Amount";
import { MonthSelector } from "../components/MonthSelector";
import { PendingOccurrences } from "../components/PendingOccurrences";
import { useExpenseCapture } from "../components/useExpenseCapture";
import { dismissAlert, listAlerts } from "../lib/api";
import { IpcError } from "../lib/ipc";
import { useSummary } from "../lib/use-summary";
import type { Alert } from "../lib/types";
import styles from "./Screens.module.css";

export type ScreenProps = {
  month: string;
  onMonthChange: (month: string) => void;
  revision: number;
  onRegistered: () => void;
  onAuthRequired: () => void;
};

/**
 * Mapa do mês: renda, alocado, não alocado e alertas de saldo negativo.
 *
 * Os totais vêm de `GET /api/v1/ledger/summary` como strings decimais; o
 * cliente não recalcula nada (ADR-006, ADR-0029).
 */
export function OverviewScreen({
  month,
  onMonthChange,
  revision,
  onRegistered,
  onAuthRequired,
}: ScreenProps) {
  const { state } = useSummary(month, revision, onAuthRequired);
  const envelopes = state.kind === "ready" ? state.summary.envelopes : [];
  const { dialog } = useExpenseCapture(envelopes, onRegistered);

  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [alertError, setAlertError] = useState<string | null>(null);
  const [alertRevision, setAlertRevision] = useState(0);

  useEffect(() => {
    let active = true;
    listAlerts()
      .then((reply) => {
        if (active) {
          setAlerts(reply.items);
          setAlertError(null);
        }
      })
      .catch((error: Error) => {
        if (!active) return;
        if (error instanceof IpcError && error.authRequired) {
          onAuthRequired();
          return;
        }
        setAlertError(error.message);
      });
    return () => {
      active = false;
    };
  }, [alertRevision, revision, onAuthRequired]);

  const handleDismiss = useCallback(async (id: string) => {
    setAlertError(null);
    try {
      await dismissAlert(id);
      // Recarrega a lista: a API decide o que continua ativo.
      setAlertRevision((n) => n + 1);
    } catch (error) {
      // Recusa da API mantém o alerta visível, sem afirmar que foi dispensado.
      setAlertError(
        error instanceof Error
          ? `O alerta não foi dispensado: ${error.message}`
          : "O alerta não foi dispensado.",
      );
    }
  }, []);

  return (
    <div className={styles.screen}>
      <header className={styles.header}>
        <h1 className={styles.title}>Visão geral</h1>
        <MonthSelector month={month} onChange={onMonthChange} />
      </header>

      {state.kind === "loading" ? <p role="status">Carregando o resumo…</p> : null}
      {state.kind === "error" ? (
        <p className={styles.error} role="alert">
          {state.message}
        </p>
      ) : null}

      {state.kind === "ready" ? (
        <section className={styles.cards} aria-label="Resumo do mês">
          <div className={styles.card}>
            <p className={styles.cardLabel}>Renda do mês</p>
            {state.summary.income ? (
              <Amount
                className={styles.cardValue}
                plain={state.summary.income.amount}
              />
            ) : (
              <>
                {/* Sem renda configurada não se inventa "0.00". */}
                <p className={styles.cardValue}>—</p>
                <p className={styles.cardNote}>
                  Renda não configurada neste mês. Configure na web.
                </p>
              </>
            )}
          </div>
          <div className={styles.card}>
            <p className={styles.cardLabel}>Alocado em verbas</p>
            <Amount
              className={styles.cardValue}
              plain={state.summary.allocated.amount}
            />
          </div>
          <div className={styles.card}>
            <p className={styles.cardLabel}>Não alocado</p>
            <Amount
              className={styles.cardValue}
              plain={state.summary.unallocated.amount}
            />
          </div>
        </section>
      ) : null}

      {/* Confirmar recorrência recarrega o resumo pelo mesmo caminho do gasto manual. */}
      <PendingOccurrences onChanged={onRegistered} onAuthRequired={onAuthRequired} revision={revision} />

      <section aria-labelledby="titulo-alertas">
        <h2 className={styles.sectionTitle} id="titulo-alertas">
          Alertas de saldo negativo
        </h2>

        {alertError ? (
          <p className={styles.error} role="alert">
            {alertError}
          </p>
        ) : null}

        {alerts.length === 0 ? (
          <p className={styles.cardNote}>Nenhum alerta ativo.</p>
        ) : (
          <ul className={styles.alerts}>
            {alerts.map((alert) => (
              <li className={styles.alert} key={alert.id}>
                <WarningCircle className={styles.alertIcon} size={20} aria-hidden />
                <span className={styles.alertText}>
                  A verba {alert.envelopeName} está com saldo negativo em{" "}
                  {alert.referenceMonth}.
                </span>
                <button
                  className={styles.alertAction}
                  type="button"
                  onClick={() => void handleDismiss(alert.id)}
                >
                  Dispensar
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {state.kind === "ready" && envelopes.length > 0 ? (
        <p className={styles.cardNote}>
          <Plus size={20} aria-hidden /> Use Ctrl+N para registrar um gasto.
        </p>
      ) : null}

      {dialog}
    </div>
  );
}
