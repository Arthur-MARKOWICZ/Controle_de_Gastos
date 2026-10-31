"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, ApiError } from "../../lib/api";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import styles from "./EnvelopeForm.module.css";

export type EnvelopePurpose = "LIMIT" | "GOAL" | "FIXED" | "SAVINGS_TARGET" | "ANNUAL_EXPENSE";

const PURPOSE_OPTIONS: { value: EnvelopePurpose; label: string }[] = [
  { value: "LIMIT", label: "Limite de gasto" },
  { value: "GOAL", label: "Meta de aporte" },
  { value: "FIXED", label: "Compromisso fixo" },
  { value: "SAVINGS_TARGET", label: "Meta de acumulação" },
  { value: "ANNUAL_EXPENSE", label: "Gasto anual" },
];

type Props = {
  onSuccess(): void;
  onCancel(): void;
  initialPurpose?: EnvelopePurpose;
  allowedPurposes?: EnvelopePurpose[];
  onOpenGlossary?(): void;
};

export function EnvelopeForm({
  onSuccess,
  onCancel,
  initialPurpose = "LIMIT",
  allowedPurposes,
  onOpenGlossary,
}: Props) {
  const { client } = useAuth();
  const api = createApiClient(client);
  const options = allowedPurposes
    ? PURPOSE_OPTIONS.filter((option) => allowedPurposes.includes(option.value))
    : PURPOSE_OPTIONS;
  const startingPurpose = options.some((option) => option.value === initialPurpose)
    ? initialPurpose
    : (options[0]?.value ?? "LIMIT");
  const [name, setName] = useState("");
  const [purpose, setPurpose] = useState<EnvelopePurpose>(startingPurpose);
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
      {error && <Notice tone="danger">{error}</Notice>}
      <Field id="envelope-name" label="Nome da verba">
        <input value={name} onChange={e => setName(e.target.value)} maxLength={80} required placeholder="Ex: Combustível" />
      </Field>
      <Field id="envelope-purpose" label="Natureza">
        <select value={purpose} onChange={e => setPurpose(e.target.value as EnvelopePurpose)}>
          {options.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </Field>
      {onOpenGlossary && (
        <p className={styles.glossaryHint}>
          Em dúvida sobre o tipo?{" "}
          <Button type="button" variant="text" onClick={onOpenGlossary}>Ver glossário</Button>
        </p>
      )}
      {purpose === "SAVINGS_TARGET" ? (
        <Field
          id="envelope-target"
          label="Valor total da meta (BRL)"
          hint="Aporte qualquer valor até chegar ao alvo. Esta verba não recebe alocação mensal."
        >
          <input
            inputMode="decimal"
            value={targetMask}
            onChange={e => setTargetMask(formatBRLInputMask(e.target.value))}
            placeholder="1.000,00"
            required
          />
        </Field>
      ) : purpose === "ANNUAL_EXPENSE" ? (
        <>
          <Field id="envelope-annual-amount" label="Valor anual (BRL)">
            <input
              inputMode="decimal"
              value={targetMask}
              onChange={e => setTargetMask(formatBRLInputMask(e.target.value))}
              placeholder="1.000,00"
              required
            />
          </Field>
          <Field id="envelope-due-month" label="Mês do vencimento">
            <input type="number" min="1" max="12" value={dueMonth} onChange={e => setDueMonth(e.target.value)} required />
          </Field>
          <Field id="envelope-due-day" label="Dia do vencimento">
            <input type="number" min="1" max="31" value={dueDay} onChange={e => setDueDay(e.target.value)} required />
          </Field>
          <Field id="envelope-funding-mode" label="Como reservar" hint="O gasto é sempre registrado manualmente. No modo mensal, o saldo reservado não reinicia.">
            <select value={fundingMode} onChange={e => setFundingMode(e.target.value as "MONTHLY" | "ONE_TIME")}>
              <option value="MONTHLY">Uma parcela por mês até o vencimento</option>
              <option value="ONE_TIME">Pagar somente no vencimento</option>
            </select>
          </Field>
        </>
      ) : (
        <Field id="envelope-amount" label="Valor-base (BRL)" hint="Use vírgula para centavos (ex.: 400,00).">
          <input
            inputMode="decimal"
            value={amountMask}
            onChange={e => setAmountMask(formatBRLInputMask(e.target.value))}
            placeholder="0,00"
            required
          />
        </Field>
      )}
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancelar</Button>
        <Button type="submit" busy={busy}>{busy ? "Salvando…" : "Criar verba"}</Button>
      </div>
    </form>
  );
}
