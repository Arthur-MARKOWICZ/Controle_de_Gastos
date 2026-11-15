"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import type { ApiClient, EnvelopePurpose, RecurringEntryDTO, RecurringEntryRequest } from "../../lib/api";
import { formatBRL, formatBRLInputMask, maskToPlain } from "../../lib/money";
import { isContributionPurpose } from "../EnvelopeCard/EnvelopeCard";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import styles from "./RecurringEntriesPanel.module.css";

type Props = {
  client: ApiClient;
  envelope: { id: string; name: string; purpose: EnvelopePurpose };
  onClose(): void;
};

type FormState = { editingId: string | null; description: string; amountMask: string; day: string };

const EMPTY_FORM: FormState = { editingId: null, description: "", amountMask: "", day: "" };

function summary(entry: RecurringEntryDTO) {
  return `${formatBRL(entry.amount.amount)} · todo dia ${entry.dayOfMonth} · ${entry.active ? "Ativa" : "Inativa"}`;
}

function label(entry: RecurringEntryDTO) {
  return entry.description ?? `Recorrência do dia ${entry.dayOfMonth}`;
}

/** Recorrências mensais da verba; só o proprietário chega aqui (ADR-0031). */
export function RecurringEntriesPanel({ client, envelope, onClose }: Props) {
  const kind = isContributionPurpose(envelope.purpose) ? "CONTRIBUTION" : "EXPENSE";
  const [items, setItems] = useState<RecurringEntryDTO[] | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<{ amount?: string; day?: string }>({});
  const [busy, setBusy] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    void client.listRecurringEntries(envelope.id)
      .then(setItems)
      .catch(() => setError("Não foi possível carregar as recorrências."));
  }, [client, envelope.id]);

  useEffect(() => {
    const timer = window.setTimeout(() => load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  function request(): RecurringEntryRequest | null {
    const plain = maskToPlain(form.amountMask);
    const day = Number(form.day);
    const errors: { amount?: string; day?: string } = {};
    if (!plain || !/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") errors.amount = "Informe um valor positivo";
    if (!Number.isInteger(day) || day < 1 || day > 31) errors.day = "Escolha um dia entre 1 e 31";
    setFieldError(errors);
    if (errors.amount || errors.day) return null;
    return { kind, amount: { amount: plain, currency: "BRL" }, dayOfMonth: day, description: form.description.trim() || null };
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const body = request();
    if (!body) return;
    setBusy(true);
    setError(null);
    try {
      if (form.editingId) {
        const current = items?.find((item) => item.id === form.editingId);
        const updated = await client.updateRecurringEntry(envelope.id, form.editingId, { ...body, active: current?.active ?? true });
        setItems((list) => (list ?? []).map((item) => (item.id === updated.id ? updated : item)));
      } else {
        const created = await client.createRecurringEntry(envelope.id, body);
        setItems((list) => [...(list ?? []), created]);
      }
      setForm(EMPTY_FORM);
    } catch (failure) {
      setError(failure instanceof Error && failure.message ? failure.message : "Não foi possível salvar a recorrência.");
    } finally {
      setBusy(false);
    }
  }

  async function toggle(entry: RecurringEntryDTO) {
    setBusy(true);
    setError(null);
    try {
      const updated = await client.updateRecurringEntry(envelope.id, entry.id, {
        kind: entry.kind, amount: entry.amount, dayOfMonth: entry.dayOfMonth, description: entry.description, active: !entry.active,
      });
      setItems((list) => (list ?? []).map((item) => (item.id === updated.id ? updated : item)));
    } catch {
      setError("Não foi possível alterar a recorrência.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(entry: RecurringEntryDTO) {
    setBusy(true);
    setError(null);
    try {
      await client.deleteRecurringEntry(envelope.id, entry.id);
      setItems((list) => (list ?? []).filter((item) => item.id !== entry.id));
      setPendingDelete(null);
    } catch {
      setError("Não foi possível excluir a recorrência.");
    } finally {
      setBusy(false);
    }
  }

  function edit(entry: RecurringEntryDTO) {
    setFieldError({});
    setForm({
      editingId: entry.id,
      description: entry.description ?? "",
      amountMask: formatBRLInputMask(entry.amount.amount.replace(".", "")),
      day: String(entry.dayOfMonth),
    });
  }

  const dayNumber = Number(form.day);
  const shortMonthHint = dayNumber >= 29 && dayNumber <= 31
    ? "Em meses mais curtos, a data será o último dia do mês."
    : undefined;

  return (
    <div className={styles.panel}>
      <p className={styles.lead}>
        Todo mês, no dia escolhido, <strong>{envelope.name}</strong> recebe uma pendência para você confirmar,
        ajustar ou descartar. Nada entra no saldo antes da confirmação.
      </p>

      {error && <Notice tone="danger">{error}</Notice>}

      <ul className={styles.list} aria-label={`Recorrências de ${envelope.name}`}>
        {items === null && !error && <li className={styles.empty}>Carregando…</li>}
        {items?.length === 0 && <li className={styles.empty}>Nenhuma recorrência ainda.</li>}
        {items?.map((entry) => (
          <li key={entry.id} className={styles.item}>
            <div className={styles.itemText}>
              <strong>{label(entry)}</strong>
              <span className={entry.active ? undefined : styles.inactive}>{summary(entry)}</span>
            </div>
            <div className={styles.itemActions}>
              {pendingDelete === entry.id ? (
                <>
                  <Button variant="secondary" disabled={busy} onClick={() => setPendingDelete(null)}>Manter</Button>
                  <Button disabled={busy} aria-label={`Confirmar exclusão de ${label(entry)}`} onClick={() => void remove(entry)}>
                    Excluir de vez
                  </Button>
                </>
              ) : (
                <>
                  <Button variant="text" disabled={busy} aria-label={`Editar ${label(entry)}`} onClick={() => edit(entry)}>Editar</Button>
                  <Button variant="text" disabled={busy} aria-label={`${entry.active ? "Desativar" : "Reativar"} ${label(entry)}`}
                    onClick={() => void toggle(entry)}>
                    {entry.active ? "Desativar" : "Reativar"}
                  </Button>
                  <Button variant="text" disabled={busy} aria-label={`Excluir ${label(entry)}`} onClick={() => setPendingDelete(entry.id)}>
                    Excluir
                  </Button>
                </>
              )}
            </div>
          </li>
        ))}
      </ul>

      <form className={styles.form} onSubmit={(event) => void submit(event)} noValidate>
        <h3 className={styles.formTitle}>{form.editingId ? "Editar recorrência" : `Nova recorrência de ${kind === "CONTRIBUTION" ? "aporte" : "gasto"}`}</h3>
        <Field id="recurring-description" label="Descrição (opcional)">
          <input value={form.description} maxLength={140} placeholder="Ex.: Aluguel"
            onChange={(event) => setForm({ ...form, description: event.target.value })} />
        </Field>
        <Field id="recurring-amount" label="Valor sugerido (BRL)" hint="Na confirmação você pode ajustar o valor." error={fieldError.amount}>
          <input inputMode="decimal" placeholder="0,00" value={form.amountMask}
            onChange={(event) => setForm({ ...form, amountMask: formatBRLInputMask(event.target.value) })} />
        </Field>
        <Field id="recurring-day" label="Dia do mês" hint={shortMonthHint} error={fieldError.day}>
          <input type="number" inputMode="numeric" min={1} max={31} value={form.day}
            onChange={(event) => setForm({ ...form, day: event.target.value })} />
        </Field>
        <div className={styles.actions}>
          <Button type="submit" busy={busy}>{form.editingId ? "Salvar alterações" : "Adicionar recorrência"}</Button>
          {form.editingId && (
            <Button type="button" variant="secondary" disabled={busy} onClick={() => { setForm(EMPTY_FORM); setFieldError({}); }}>
              Cancelar edição
            </Button>
          )}
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>Fechar</Button>
        </div>
      </form>
    </div>
  );
}
