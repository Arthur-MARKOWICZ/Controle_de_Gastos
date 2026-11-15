import { useCallback, useEffect, useState } from "react";
import { confirmOccurrence, discardOccurrence, listPendingOccurrences } from "../lib/api";
import { formatBRL, maskPtBR, toPlainAmount } from "../lib/money";
import { IpcError } from "../lib/ipc";
import { todaySaoPaulo } from "../lib/dates";
import type { PendingOccurrence, PendingOccurrences as Pending } from "../lib/types";
import styles from "./Recurring.module.css";

type Props = {
  /** Saldos e alertas mudam depois de confirmar. */
  onChanged: () => void;
  onAuthRequired: () => void;
  revision?: number;
  /** Injetável no teste; por padrão, hoje em America/Sao_Paulo. */
  today?: string;
};

function label(item: PendingOccurrence): string {
  return item.description ?? item.envelopeName;
}

function dateBR(iso: string): string {
  const [year, month, day] = iso.split("-");
  return `${day}/${month}/${year}`;
}

/**
 * Pendências de recorrência do mês e vencidas (ADR-0031). O cliente não
 * calcula saldo: depois de confirmar, quem recarrega o resumo é a tela.
 */
export function PendingOccurrences({ onChanged, onAuthRequired, revision = 0, today }: Props) {
  const [data, setData] = useState<Pending | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [adjusting, setAdjusting] = useState<{ id: string; masked: string; date: string } | null>(null);
  const [discarding, setDiscarding] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  const handleFailure = useCallback((failure: unknown, fallback: string) => {
    if (failure instanceof IpcError && failure.authRequired) {
      onAuthRequired();
      return;
    }
    setError(failure instanceof Error && failure.message ? failure.message : fallback);
  }, [onAuthRequired]);

  useEffect(() => {
    let active = true;
    listPendingOccurrences()
      .then((reply) => { if (active) setData(reply); })
      .catch((failure: unknown) => { if (active) handleFailure(failure, "Não foi possível carregar as pendências."); });
    return () => { active = false; };
  }, [revision, reload, handleFailure]);

  async function resolve(id: string, action: () => Promise<unknown>, fallback: string) {
    setBusyId(id);
    setError(null);
    try {
      await action();
      setData((current) => current && {
        current: current.current.filter((item) => item.id !== id),
        overdue: current.overdue.filter((item) => item.id !== id),
      });
      setAdjusting(null);
      setDiscarding(null);
      onChanged();
    } catch (failure) {
      handleFailure(failure, fallback);
      setReload((n) => n + 1);
    } finally {
      setBusyId(null);
    }
  }

  if (!data && !error) return null;
  const total = (data?.current.length ?? 0) + (data?.overdue.length ?? 0);
  if (total === 0 && !error) return null;

  function confirmAdjusted(event: React.FormEvent, item: PendingOccurrence) {
    event.preventDefault();
    if (!adjusting) return;
    const plain = toPlainAmount(adjusting.masked);
    if (!plain) {
      setError("Informe um valor em reais com duas casas decimais, maior que zero.");
      return;
    }
    const occurredAt = adjusting.date;
    void resolve(item.id, () => confirmOccurrence(item.id, { amount: plain, occurredAt }),
      "A pendência não foi confirmada.");
  }

  function renderItem(item: PendingOccurrence, overdue: boolean) {
    const busy = busyId === item.id;
    const name = label(item);
    return (
      <li className={styles.item} key={item.id}>
        <div className={styles.text}>
          <span className={styles.name}>{name}</span>
          <span className={styles.meta}>
            {item.envelopeName} · {formatBRL(item.suggestedAmount.amount)} ·{" "}
            {overdue ? "venceu em" : "previsto para"} {dateBR(item.dueDate)}
          </span>
        </div>
        {adjusting?.id === item.id ? (
          <form className={styles.adjust} onSubmit={(event) => confirmAdjusted(event, item)} noValidate>
            <label className={styles.field}>
              Valor
              <input inputMode="decimal" value={adjusting.masked}
                onChange={(event) => setAdjusting({ ...adjusting, masked: maskPtBR(event.target.value) })} />
            </label>
            <label className={styles.field}>
              Data
              <input type="date" value={adjusting.date} max={today ?? todaySaoPaulo()}
                onChange={(event) => setAdjusting({ ...adjusting, date: event.target.value })} />
            </label>
            <button className={styles.primary} type="submit" disabled={busy}>Confirmar com ajuste</button>
            <button className={styles.secondary} type="button" disabled={busy} onClick={() => setAdjusting(null)}>
              Cancelar
            </button>
          </form>
        ) : discarding === item.id ? (
          <div className={styles.actions}>
            <span className={styles.meta}>Nenhum lançamento será criado neste mês.</span>
            <button className={styles.secondary} type="button" disabled={busy}
              aria-label={`Sim, descartar ${name}`}
              onClick={() => void resolve(item.id, () => discardOccurrence(item.id), "A pendência não foi descartada.")}>
              Sim, descartar
            </button>
            <button className={styles.secondary} type="button" disabled={busy} onClick={() => setDiscarding(null)}>
              Manter
            </button>
          </div>
        ) : (
          <div className={styles.actions}>
            <button className={styles.primary} type="button" disabled={busy} aria-label={`Confirmar ${name}`}
              onClick={() => void resolve(item.id, () => confirmOccurrence(item.id), "A pendência não foi confirmada.")}>
              Confirmar
            </button>
            <button className={styles.secondary} type="button" disabled={busy} aria-label={`Ajustar ${name}`}
              onClick={() => setAdjusting({
                id: item.id,
                masked: maskPtBR(item.suggestedAmount.amount.replace(".", "")),
                date: item.dueDate,
              })}>
              Ajustar
            </button>
            <button className={styles.secondary} type="button" disabled={busy} aria-label={`Descartar ${name}`}
              onClick={() => setDiscarding(item.id)}>
              Descartar
            </button>
          </div>
        )}
      </li>
    );
  }

  return (
    <section className={styles.block} aria-labelledby="titulo-recorrencias">
      <h2 className={styles.title} id="titulo-recorrencias">Recorrências para confirmar</h2>
      <p className={styles.meta}>Nada entra no saldo antes de você confirmar.</p>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      {data && data.current.length > 0 ? (
        <section aria-label="Pendências do mês">
          <h3 className={styles.group}>Pendências do mês</h3>
          <ul className={styles.list}>{data.current.map((item) => renderItem(item, false))}</ul>
        </section>
      ) : null}
      {data && data.overdue.length > 0 ? (
        <section aria-label="Vencidas">
          <h3 className={styles.group}>Vencidas</h3>
          <ul className={styles.list}>{data.overdue.map((item) => renderItem(item, true))}</ul>
        </section>
      ) : null}
    </section>
  );
}
