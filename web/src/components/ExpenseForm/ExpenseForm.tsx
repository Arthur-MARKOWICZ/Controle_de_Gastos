"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, ApiError, type EnvelopeDTO } from "../../lib/api";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import { todaySaoPaulo } from "../../lib/dates";
import styles from "../EnvelopeForm/EnvelopeForm.module.css";

type Props = {
  envelope: EnvelopeDTO;
  kind?: "EXPENSE" | "CONTRIBUTION";
  onSuccess(targetJustReached?: boolean): void;
  onCancel(): void;
};

export function ExpenseForm({ envelope, kind = "EXPENSE", onSuccess, onCancel }: Props) {
  const { client } = useAuth();
  const api = createApiClient(client);
  const [amountMask, setAmountMask] = useState("");
  const [occurredAt, setOccurredAt] = useState(todaySaoPaulo());
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
      onSuccess(entry.targetJustReached);
    } catch (err) {
      if (err instanceof ApiError) setError(err.detail || `Não foi possível registrar o ${kind === "EXPENSE" ? "gasto" : "aporte"}`);
      else setError("Erro inesperado");
    } finally { setBusy(false); }
  }

  return (
    <form onSubmit={submit} className={styles.form}>
      {error && <p role="alert" className={styles.error}>{error}</p>}
      <p className={styles.context}>Verba: <strong>{envelope.name}</strong></p>
      <label htmlFor="expense-amount">Valor (BRL)</label>
      <input id="expense-amount" inputMode="decimal" value={amountMask} onChange={e => setAmountMask(formatBRLInputMask(e.target.value))} placeholder="0,00" required aria-describedby="expense-help" />
      <span id="expense-help" className={styles.help}>Ex: 120,00 será enviado como 120.00</span>
      <label htmlFor="expense-date">Data</label>
      <input id="expense-date" type="date" value={occurredAt} onChange={e => setOccurredAt(e.target.value)} required max={todaySaoPaulo()} />
      <label htmlFor="expense-desc">Descrição (opcional)</label>
      <input id="expense-desc" value={description} onChange={e => setDescription(e.target.value)} maxLength={140} placeholder={kind === "EXPENSE" ? "Ex: Posto Avenida" : "Ex: Reserva de setembro"} />
      <div className={styles.actions}>
        <button type="button" onClick={onCancel} disabled={busy}>Cancelar</button>
        <button type="submit" disabled={busy} className={styles.primary}>{busy ? "Registrando…" : kind === "EXPENSE" ? "Registrar gasto" : "Registrar aporte"}</button>
      </div>
    </form>
  );
}
