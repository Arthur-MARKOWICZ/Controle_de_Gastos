"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, ApiError } from "../../lib/api";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import styles from "./EnvelopeForm.module.css";

type Props = {
  onSuccess(): void;
  onCancel(): void;
  initialPurpose?: "LIMIT" | "GOAL" | "FIXED" | "SAVINGS_TARGET" | "ANNUAL_EXPENSE";
};

export function EnvelopeForm({ onSuccess, onCancel, initialPurpose = "LIMIT" }: Props) {
  const { client } = useAuth();
  const api = createApiClient(client);
  const [name, setName] = useState("");
  const [purpose, setPurpose] = useState<"LIMIT" | "GOAL" | "FIXED" | "SAVINGS_TARGET" | "ANNUAL_EXPENSE">(initialPurpose);
  const [amountMask, setAmountMask] = useState("");
  const [targetMask, setTargetMask] = useState("");
  const [dueMonth, setDueMonth] = useState("1");
  const [dueDay, setDueDay] = useState("10");
  const [fundingMode, setFundingMode] = useState<"MONTHLY" | "ONE_TIME">("MONTHLY");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const plain = purpose === "SAVINGS_TARGET" || purpose === "ANNUAL_EXPENSE" ? "0.00" : maskToPlain(amountMask);
    const targetPlain = maskToPlain(targetMask);
    if (!plain) { setError("Informe o valor em BRL"); return; }
    if (!/^\d{1,17}\.\d{2}$/.test(plain)) { setError("Valor deve ter duas casas decimais, ex: 400,00"); return; }
    if (purpose === "SAVINGS_TARGET" && (!targetPlain || !/^\d{1,17}\.\d{2}$/.test(targetPlain) || targetPlain === "0.00")) {
      setError("Informe um alvo maior que zero, ex: 1.000,00"); return;
    }
    if (purpose === "ANNUAL_EXPENSE" && (!targetPlain || !/^\d{1,17}\.\d{2}$/.test(targetPlain) || targetPlain === "0.00" || Number(dueMonth) < 1 || Number(dueMonth) > 12 || Number(dueDay) < 1 || Number(dueDay) > 31)) {
      setError("Informe valor anual e uma data de vencimento válida."); return;
    }
    if (name.trim().length === 0 || name.trim().length > 80) { setError("Nome deve ter entre 1 e 80 caracteres"); return; }
    setBusy(true);
    try {
      await api.createEnvelope({ name: name.trim(), purpose, baseAmount: { amount: plain, currency: "BRL" },
        ...(purpose === "SAVINGS_TARGET" ? { targetAmount: { amount: targetPlain, currency: "BRL" } } : {}),
        ...(purpose === "ANNUAL_EXPENSE" ? { annualAmount: { amount: targetPlain, currency: "BRL" }, dueMonth: Number(dueMonth), dueDay: Number(dueDay), fundingMode } : {}) });
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
        <option value="ANNUAL_EXPENSE">Gasto anual</option>
      </select>
      {purpose === "SAVINGS_TARGET" ? (
        <>
          <label htmlFor="envelope-target">Valor total da meta (BRL)</label>
          <input id="envelope-target" inputMode="decimal" value={targetMask} onChange={e => setTargetMask(formatBRLInputMask(e.target.value))} placeholder="1.000,00" required aria-describedby="target-help" />
          <span id="target-help" className={styles.help}>Aporte qualquer valor até chegar ao alvo. Esta verba não recebe alocação mensal.</span>
        </>
      ) : purpose === "ANNUAL_EXPENSE" ? (
        <>
          <label htmlFor="envelope-annual-amount">Valor anual (BRL)</label>
          <input id="envelope-annual-amount" inputMode="decimal" value={targetMask} onChange={e => setTargetMask(formatBRLInputMask(e.target.value))} placeholder="1.000,00" required />
          <label htmlFor="envelope-due-month">Mês do vencimento</label>
          <input id="envelope-due-month" type="number" min="1" max="12" value={dueMonth} onChange={e => setDueMonth(e.target.value)} required />
          <label htmlFor="envelope-due-day">Dia do vencimento</label>
          <input id="envelope-due-day" type="number" min="1" max="31" value={dueDay} onChange={e => setDueDay(e.target.value)} required />
          <label htmlFor="envelope-funding-mode">Como reservar</label>
          <select id="envelope-funding-mode" value={fundingMode} onChange={e => setFundingMode(e.target.value as "MONTHLY" | "ONE_TIME")}>
            <option value="MONTHLY">Uma parcela por mês até o vencimento</option>
            <option value="ONE_TIME">Pagar somente no vencimento</option>
          </select>
          <span className={styles.help}>O gasto é sempre registrado manualmente. No modo mensal, o saldo reservado não reinicia.</span>
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
