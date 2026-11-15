"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { ApiError, type ApiClient, type PendingOccurrenceDTO, type PendingOccurrencesDTO } from "../../lib/api";
import { formatDateLabel, todaySaoPaulo } from "../../lib/dates";
import { formatBRL, formatBRLInputMask, maskToPlain } from "../../lib/money";
import { Button } from "../ui/Button";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import styles from "./PendingOccurrences.module.css";

type Props = {
  client: ApiClient;
  /** Saldos, alertas e listas mudam após confirmar. */
  onChanged(): void;
  refreshKey?: number | string;
};

function label(item: PendingOccurrenceDTO) {
  return item.description ?? item.envelopeName;
}

/** Pendências de recorrência do mês e vencidas, com confirmar, ajustar e descartar (ADR-0031). */
export function PendingOccurrences({ client, onChanged, refreshKey = 0 }: Props) {
  const [data, setData] = useState<PendingOccurrencesDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [adjusting, setAdjusting] = useState<{ id: string; amountMask: string; date: string } | null>(null);
  const [adjustError, setAdjustError] = useState<string | null>(null);
  const [discardTarget, setDiscardTarget] = useState<PendingOccurrenceDTO | null>(null);

  const load = useCallback(() => {
    // Promise.resolve: uma falha síncrona do cliente também vira mensagem, não erro solto.
    void Promise.resolve()
      .then(() => client.listPendingOccurrences())
      .then(setData)
      .catch(() => setError("Não foi possível carregar as pendências de recorrência."));
  }, [client]);

  useEffect(() => {
    const timer = window.setTimeout(() => load(), 0);
    return () => window.clearTimeout(timer);
  }, [load, refreshKey]);

  function removeItem(id: string) {
    setData((current) => current && {
      current: current.current.filter((item) => item.id !== id),
      overdue: current.overdue.filter((item) => item.id !== id),
    });
  }

  async function run(id: string, action: () => Promise<unknown>, failure: string) {
    setBusyId(id);
    setError(null);
    try {
      await action();
      removeItem(id);
      setAdjusting(null);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError && err.detail ? err.detail : failure);
      load();
    } finally {
      setBusyId(null);
    }
  }

  function confirmAdjusted(event: FormEvent) {
    event.preventDefault();
    if (!adjusting) return;
    const plain = maskToPlain(adjusting.amountMask);
    if (!plain || !/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") {
      setAdjustError("Informe um valor positivo");
      return;
    }
    setAdjustError(null);
    const { id, date } = adjusting;
    void run(id, () => client.confirmOccurrence(id, { amount: { amount: plain, currency: "BRL" }, occurredAt: date }),
      "Não foi possível confirmar a pendência.");
  }

  if (!data && !error) return null;
  const total = (data?.current.length ?? 0) + (data?.overdue.length ?? 0);
  if (total === 0 && !error) return null;

  function renderItem(item: PendingOccurrenceDTO, overdue: boolean) {
    const busy = busyId === item.id;
    const isAdjusting = adjusting?.id === item.id;
    return (
      <li key={item.id} className={styles.item}>
        <div className={styles.itemText}>
          <strong>{label(item)}</strong>
          <span>
            {item.envelopeName} · {formatBRL(item.suggestedAmount.amount)} · {overdue ? "venceu em" : "previsto para"}{" "}
            {formatDateLabel(item.dueDate)}
          </span>
        </div>
        {isAdjusting ? (
          <form className={styles.adjust} onSubmit={confirmAdjusted} noValidate>
            <Field id={`adjust-amount-${item.id}`} label="Valor (BRL)" error={adjustError}>
              <input inputMode="decimal" value={adjusting.amountMask}
                onChange={(event) => setAdjusting({ ...adjusting, amountMask: formatBRLInputMask(event.target.value) })} />
            </Field>
            <Field id={`adjust-date-${item.id}`} label="Data">
              <input type="date" value={adjusting.date} max={todaySaoPaulo()}
                onChange={(event) => setAdjusting({ ...adjusting, date: event.target.value })} />
            </Field>
            <div className={styles.actions}>
              <Button type="submit" busy={busy}>Confirmar com ajuste</Button>
              <Button type="button" variant="secondary" disabled={busy} onClick={() => { setAdjusting(null); setAdjustError(null); }}>
                Cancelar
              </Button>
            </div>
          </form>
        ) : (
          <div className={styles.actions}>
            <Button busy={busy} aria-label={`Confirmar ${label(item)}`}
              onClick={() => void run(item.id, () => client.confirmOccurrence(item.id), "Não foi possível confirmar a pendência.")}>
              Confirmar
            </Button>
            <Button variant="secondary" disabled={busy} aria-label={`Ajustar ${label(item)}`}
              onClick={() => setAdjusting({
                id: item.id,
                amountMask: formatBRLInputMask(item.suggestedAmount.amount.replace(".", "")),
                date: item.dueDate,
              })}>
              Ajustar
            </Button>
            <Button variant="text" disabled={busy} aria-label={`Descartar ${label(item)}`} onClick={() => setDiscardTarget(item)}>
              Descartar
            </Button>
          </div>
        )}
      </li>
    );
  }

  return (
    <section className={styles.block} aria-labelledby="pendencias-titulo">
      <h2 id="pendencias-titulo" className={styles.title}>Recorrências para confirmar</h2>
      <p className={styles.lead}>Nada entra no saldo antes de você confirmar.</p>
      {error && <Notice tone="danger">{error}</Notice>}
      {data && data.current.length > 0 && (
        <section aria-label="Pendências do mês">
          <h3 className={styles.groupTitle}>Pendências do mês</h3>
          <ul className={styles.list}>{data.current.map((item) => renderItem(item, false))}</ul>
        </section>
      )}
      {data && data.overdue.length > 0 && (
        <section aria-label="Vencidas">
          <h3 className={styles.groupTitle}>Vencidas</h3>
          <ul className={styles.list}>{data.overdue.map((item) => renderItem(item, true))}</ul>
        </section>
      )}
      <ConfirmDialog
        open={!!discardTarget}
        title={discardTarget ? `Descartar ${label(discardTarget)}?` : "Descartar pendência?"}
        message="Nenhum lançamento será criado para este mês. A recorrência continua ativa nos próximos meses."
        confirmLabel="Descartar"
        onConfirm={() => {
          const target = discardTarget;
          setDiscardTarget(null);
          if (target) void run(target.id, () => client.discardOccurrence(target.id), "Não foi possível descartar a pendência.");
        }}
        onCancel={() => setDiscardTarget(null)}
      />
    </section>
  );
}
