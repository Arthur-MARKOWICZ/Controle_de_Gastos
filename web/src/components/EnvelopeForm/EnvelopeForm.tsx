"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, ApiError } from "../../lib/api";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import styles from "./EnvelopeForm.module.css";

type Props = {
  onSuccess(): void;
  onCancel(): void;
  initialPurpose?: "LIMIT" | "GOAL" | "FIXED" | "SAVINGS_TARGET";
};

export function EnvelopeForm({ onSuccess, onCancel, initialPurpose = "LIMIT" }: Props) {
  const { client } = useAuth();
  const api = createApiClient(client);
  const [name, setName] = useState("");
  const [purpose, setPurpose] = useState<"LIMIT" | "GOAL" | "FIXED" | "SAVINGS_TARGET">(initialPurpose);
  const [amountMask, setAmountMask] = useState("");
  const [targetMask, setTargetMask] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const plain = purpose === "SAVINGS_TARGET" ? "0.00" : maskToPlain(amountMask);
    const targetPlain = maskToPlain(targetMask);
    if (!plain) { setError("Informe o valor em BRL"); return; }
    if (!/^\d{1,17}\.\d{2}$/.test(plain)) { setError("Valor deve ter duas casas decimais, ex: 400,00"); return; }
    if (purpose === "SAVINGS_TARGET" && (!targetPlain || !/^\d{1,17}\.\d{2}$/.test(targetPlain) || targetPlain === "0.00")) {
      setError("Informe um alvo maior que zero, ex: 1.000,00"); return;
    }
    if (name.trim().length === 0 || name.trim().length > 80) { setError("Nome deve ter entre 1 e 80 caracteres"); return; }
    setBusy(true);
    try {
      await api.createEnvelope({ name: name.trim(), purpose, baseAmount: { amount: plain, currency: "BRL" },
        ...(purpose === "SAVINGS_TARGET" ? { targetAmount: { amount: targetPlain, currency: "BRL" } } : {}) });
      onSuccess();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "ALLOCATION_EXCEEDS_INCOME") setError(`Soma das verbas excede a renda (excesso ${err.detail || plain})`);
        else if (err.status === 409) setError("Renda insuficiente para esta verba");
        else setError(err.detail || "Não foi possível criar a verba");
      } else setError("Erro inesperado");
    } finally { setBusy(false); }
  }

  return (
    <form onSubmit={submit} className={styles.form}>
      {error && <p role="alert" className={styles.error}>{error}</p>}
      <label htmlFor="envelope-name">Nome da verba</label>
      <input id="envelope-name" value={name} onChange={e => setName(e.target.value)} maxLength={80} required placeholder="Ex: Combustível" />
      <label htmlFor="envelope-purpose">Natureza</label>
      <select id="envelope-purpose" value={purpose} onChange={e => setPurpose(e.target.value as never)}>
        <option value="LIMIT">Limite de gasto</option>
        <option value="GOAL">Meta de aporte</option>
        <option value="FIXED">Compromisso fixo</option>
        <option value="SAVINGS_TARGET">Meta de acumulação</option>
      </select>
      {purpose === "SAVINGS_TARGET" ? (
        <>
          <label htmlFor="envelope-target">Valor total da meta (BRL)</label>
          <input id="envelope-target" inputMode="decimal" value={targetMask} onChange={e => setTargetMask(formatBRLInputMask(e.target.value))} placeholder="1.000,00" required aria-describedby="target-help" />
          <span id="target-help" className={styles.help}>Aporte qualquer valor até chegar ao alvo. Esta verba não recebe alocação mensal.</span>
        </>
      ) : (
        <>
          <label htmlFor="envelope-amount">Valor-base (BRL)</label>
          <input id="envelope-amount" inputMode="decimal" value={amountMask} onChange={e => setAmountMask(formatBRLInputMask(e.target.value))} placeholder="0,00" required aria-describedby="amount-help" />
          <span id="amount-help" className={styles.help}>Use vírgula para centavos. Será enviado como 400.00</span>
        </>
      )}
      <div className={styles.actions}>
        <button type="button" onClick={onCancel} disabled={busy}>Cancelar</button>
        <button type="submit" disabled={busy} className={styles.primary}>{busy ? "Salvando…" : "Criar verba"}</button>
      </div>
    </form>
  );
}
