"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, ApiError, type EnvelopeDTO } from "../../lib/api";
import { formatBRL, formatBRLInputMask, maskToPlain, subtractBRL } from "../../lib/money";
import { todaySaoPaulo } from "../../lib/dates";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import styles from "../EnvelopeForm/EnvelopeForm.module.css";

export type ExpenseResult = { targetJustReached: boolean; balanceAfter: string | null };

type Props = {
  envelope: EnvelopeDTO;
  kind?: "EXPENSE" | "CONTRIBUTION";
  onSuccess(result: ExpenseResult): void;
  onCancel(): void;
};

function isOverdrawn(balanceAfter: string | null) {
  return balanceAfter?.startsWith("-") ?? false;
}

export function overdraftNotice({ balanceAfter }: ExpenseResult) {
  return balanceAfter && isOverdrawn(balanceAfter) ? `. A verba ficou com saldo negativo de ${formatBRL(balanceAfter)}` : "";
}

export function ExpenseForm({ envelope, kind = "EXPENSE", onSuccess, onCancel }: Props) {
  const { client } = useAuth();
  const api = createApiClient(client);
  const [amountMask, setAmountMask] = useState("");
  const [occurredAt, setOccurredAt] = useState(todaySaoPaulo());
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const balanceAfter = kind === "EXPENSE" ? subtractBRL(envelope.available.amount, maskToPlain(amountMask)) : null;
  const overdrawn = isOverdrawn(balanceAfter);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const plain = maskToPlain(amountMask);
    if (!plain || !/^\d{1,17}\.\d{2}$/.test(plain)) { setError("Valor deve ter duas casas decimais"); return; }
    if (plain === "0.00") { setError("Valor deve ser positivo"); return; }
    if (description.length > 140) { setError("Descrição até 140 caracteres"); return; }
    setBusy(true);
    try {
      const entry = await api.createEntry(envelope.id, { kind, amount: { amount: plain, currency: "BRL" }, occurredAt, description: description.trim() || null });
      onSuccess({ targetJustReached: entry.targetJustReached ?? false, balanceAfter });
    } catch (err) {
      if (err instanceof ApiError) setError(err.detail || `Não foi possível registrar o ${kind === "EXPENSE" ? "gasto" : "aporte"}`);
      else setError("Erro inesperado");
    } finally { setBusy(false); }
  }

  return (
    <form onSubmit={submit} className={styles.form}>
      {error && <Notice tone="danger">{error}</Notice>}
      <p className={styles.context}>Verba: <strong>{envelope.name}</strong></p>
      <Field id="expense-amount" label="Valor (BRL)" hint="Use vírgula para centavos (ex.: 120,00).">
        <input
          inputMode="decimal"
          value={amountMask}
          onChange={e => setAmountMask(formatBRLInputMask(e.target.value))}
          placeholder="0,00"
          required
        />
      </Field>
      {kind === "EXPENSE" && (
        <div aria-live="polite" className={overdrawn ? styles.balancePreviewNegative : styles.balancePreview}>
          {balanceAfter && <p>Saldo após o gasto: {formatBRL(balanceAfter)}</p>}
          {overdrawn && <p>A verba ficará com saldo negativo. O registro continua permitido.</p>}
        </div>
      )}
      <Field id="expense-date" label="Data">
        <input type="date" value={occurredAt} onChange={e => setOccurredAt(e.target.value)} required max={todaySaoPaulo()} />
      </Field>
      <Field id="expense-desc" label="Descrição (opcional)">
        <input
          value={description}
          onChange={e => setDescription(e.target.value)}
          maxLength={140}
          placeholder={kind === "EXPENSE" ? "Ex: Posto Avenida" : "Ex: Reserva de setembro"}
        />
      </Field>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancelar</Button>
        <Button type="submit" busy={busy}>
          {busy ? "Registrando…" : kind === "EXPENSE" ? "Registrar gasto" : "Registrar aporte"}
        </Button>
      </div>
    </form>
  );
}
