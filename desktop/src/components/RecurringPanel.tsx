import { useEffect, useState } from "react";
import {
  createRecurringEntry,
  deleteRecurringEntry,
  listRecurringEntries,
  updateRecurringEntry,
} from "../lib/api";
import { IpcError } from "../lib/ipc";
import { formatBRL, maskPtBR, toPlainAmount } from "../lib/money";
import type { LedgerKind, RecurringEntry, SummaryEnvelope } from "../lib/types";
import styles from "./Recurring.module.css";

type Props = {
  envelope: SummaryEnvelope;
  onAuthRequired: () => void;
};

const CONTRIBUTION_PURPOSES = new Set(["GOAL", "SAVINGS_TARGET"]);

function summaryOf(entry: RecurringEntry): string {
  return `${formatBRL(entry.amount.amount)} · todo dia ${entry.dayOfMonth} · ${entry.active ? "Ativa" : "Inativa"}`;
}

/** Recorrências da verba; só o dono abre este painel (ADR-0031). */
export function RecurringPanel({ envelope, onAuthRequired }: Props) {
  const kind: LedgerKind = CONTRIBUTION_PURPOSES.has(envelope.purpose) ? "CONTRIBUTION" : "EXPENSE";
  const [items, setItems] = useState<RecurringEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [description, setDescription] = useState("");
  const [masked, setMasked] = useState("");
  const [day, setDay] = useState("");
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    listRecurringEntries(envelope.id)
      .then((reply) => { if (active) setItems(reply); })
      .catch((failure: unknown) => {
        if (!active) return;
        if (failure instanceof IpcError && failure.authRequired) { onAuthRequired(); return; }
        setError(failure instanceof Error ? failure.message : "Não foi possível carregar as recorrências.");
      });
    return () => { active = false; };
  }, [envelope.id, onAuthRequired]);

  function fail(failure: unknown, fallback: string) {
    if (failure instanceof IpcError && failure.authRequired) { onAuthRequired(); return; }
    setError(failure instanceof Error && failure.message ? failure.message : fallback);
  }

  function replace(updated: RecurringEntry) {
    setItems((current) => (current ?? []).map((item) => (item.id === updated.id ? updated : item)));
  }

  function clearForm() {
    setEditingId(null); setDescription(""); setMasked(""); setDay("");
  }

  async function run(action: () => Promise<void>, fallback: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (failure) {
      fail(failure, fallback);
    } finally {
      setBusy(false);
    }
  }

  function toggle(entry: RecurringEntry) {
    void run(async () => {
      replace(await updateRecurringEntry(envelope.id, entry.id, {
        kind: entry.kind, amount: entry.amount.amount, dayOfMonth: entry.dayOfMonth,
        description: entry.description, active: !entry.active,
      }));
    }, "A recorrência não foi alterada.");
  }

  function remove(entry: RecurringEntry) {
    void run(async () => {
      await deleteRecurringEntry(envelope.id, entry.id);
      setItems((current) => (current ?? []).filter((item) => item.id !== entry.id));
      setPendingDelete(null);
    }, "A recorrência não foi excluída.");
  }

  function edit(entry: RecurringEntry) {
    setError(null);
    setEditingId(entry.id);
    setDescription(entry.description ?? "");
    setMasked(maskPtBR(entry.amount.amount.replace(".", "")));
    setDay(String(entry.dayOfMonth));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const plain = toPlainAmount(masked);
    const dayNumber = Number(day);
    if (!plain) { setError("Informe um valor em reais com duas casas decimais, maior que zero."); return; }
    if (!Number.isInteger(dayNumber) || dayNumber < 1 || dayNumber > 31) { setError("Escolha um dia entre 1 e 31."); return; }
    const input = { kind, amount: plain, dayOfMonth: dayNumber, description: description.trim() || null };
    const editing = editingId;
    await run(async () => {
      if (editing) {
        const current = items?.find((item) => item.id === editing);
        replace(await updateRecurringEntry(envelope.id, editing, { ...input, active: current?.active ?? true }));
      } else {
        const created = await createRecurringEntry(envelope.id, input);
        setItems((list) => [...(list ?? []), created]);
      }
      clearForm();
    }, editing ? "A recorrência não foi salva." : "A recorrência não foi criada.");
  }

  const dayNumber = Number(day);
  const ids = `rec-${envelope.id}`;
  return (
    <section className={styles.block} aria-label={`Recorrências de ${envelope.name}`}>
      <p className={styles.meta}>
        Todo mês, no dia escolhido, você recebe uma pendência para confirmar, ajustar ou descartar.
      </p>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      {items === null && !error ? <p role="status" className={styles.meta}>Carregando…</p> : null}
      {items?.length === 0 ? <p className={styles.meta}>Nenhuma recorrência ainda.</p> : null}
      {items && items.length > 0 ? (
        <ul className={styles.list}>
          {items.map((entry) => {
            const name = entry.description ?? `Recorrência do dia ${entry.dayOfMonth}`;
            return (
              <li className={styles.item} key={entry.id}>
                <div className={styles.text}>
                  <span className={styles.name}>{name}</span>
                  <span className={styles.meta}>{summaryOf(entry)}</span>
                </div>
                {pendingDelete === entry.id ? (
                  <div className={styles.actions}>
                    <span className={styles.meta}>Lançamentos já confirmados continuam no histórico.</span>
                    <button className={styles.secondary} type="button" disabled={busy}
                      aria-label={`Confirmar exclusão de ${name}`} onClick={() => remove(entry)}>
                      Excluir de vez
                    </button>
                    <button className={styles.secondary} type="button" disabled={busy} onClick={() => setPendingDelete(null)}>
                      Manter
                    </button>
                  </div>
                ) : (
                  <div className={styles.actions}>
                    <button className={styles.secondary} type="button" disabled={busy}
                      aria-label={`Editar ${name}`} onClick={() => edit(entry)}>Editar</button>
                    <button className={styles.secondary} type="button" disabled={busy}
                      aria-label={`${entry.active ? "Desativar" : "Reativar"} ${name}`} onClick={() => toggle(entry)}>
                      {entry.active ? "Desativar" : "Reativar"}
                    </button>
                    <button className={styles.secondary} type="button" disabled={busy}
                      aria-label={`Excluir ${name}`} onClick={() => setPendingDelete(entry.id)}>Excluir</button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      ) : null}
      <form className={styles.adjust} onSubmit={(event) => void submit(event)} noValidate>
        <label className={styles.field} htmlFor={`${ids}-desc`}>
          Descrição (opcional)
          <input id={`${ids}-desc`} maxLength={140} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label className={styles.field} htmlFor={`${ids}-valor`}>
          Valor sugerido
          <input id={`${ids}-valor`} inputMode="decimal" value={masked} onChange={(e) => setMasked(maskPtBR(e.target.value))} />
        </label>
        <label className={styles.field} htmlFor={`${ids}-dia`}>
          Dia do mês
          <input id={`${ids}-dia`} inputMode="numeric" value={day} aria-describedby={`${ids}-dica`}
            onChange={(e) => setDay(e.target.value.replace(/\D/g, "").slice(0, 2))} />
        </label>
        <button className={styles.primary} type="submit" disabled={busy}>
          {editingId ? "Salvar alterações" : "Adicionar recorrência"}
        </button>
        {editingId ? (
          <button className={styles.secondary} type="button" disabled={busy} onClick={clearForm}>
            Cancelar edição
          </button>
        ) : null}
        <span id={`${ids}-dica`} className={styles.meta}>
          {dayNumber >= 29 && dayNumber <= 31 ? "Em meses mais curtos, a data será o último dia do mês." : ""}
        </span>
      </form>
    </section>
  );
}
