"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { ApiError, createApiClient } from "../../lib/api";
import { formatBRL, formatBRLInputMask, maskToPlain } from "../../lib/money";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import styles from "./IncomeForm.module.css";

type Props = {
  initialAmount?: string;
  onSuccess(): void;
  onCancel(): void;
};

export function IncomeForm({ initialAmount, onSuccess, onCancel }: Props) {
  const { client } = useAuth();
  const api = createApiClient(client);
  const [amountMask, setAmountMask] = useState(() => (initialAmount ? formatBRLInputMask(initialAmount.replace(/\D/g, "")) : ""));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const plain = maskToPlain(amountMask);
    if (!plain || !/^\d{1,17}\.\d{2}$/.test(plain)) {
      setError("Valor deve ter duas casas decimais, ex: 5.000,00");
      return;
    }
    setBusy(true);
    try {
      await api.putIncome(plain);
      onSuccess();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "INCOME_BELOW_BASE_ALLOCATIONS" || err.code === "INCOME_CONCURRENT_CHANGE") {
          const required = err.requiredMinimum ? formatBRL(err.requiredMinimum) : "";
          const shortfall = err.shortfall ? formatBRL(err.shortfall) : "";
          if (required && shortfall) {
            setError(`Renda menor que verbas-base. Mínimo ${required} (faltam ${shortfall}). Reduza verbas ou aumente a renda.`);
          } else {
            setError(err.detail || "Renda menor que a soma das verbas-base");
          }
        } else if (err.status === 400) {
          setError("Valor inválido. Use o formato 5.000,00 com exatamente duas casas decimais.");
        } else {
          setError(err.detail || "Não foi possível salvar a renda");
        }
      } else {
        setError("Erro inesperado");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className={styles.form} aria-labelledby="income-form-title">
      <h3 id="income-form-title" className={styles.title}>Configurar renda mensal</h3>
      <p className={styles.supporting}>
        Vale para o mês atual. Salvar o mesmo valor de novo não cria alteração no histórico.
      </p>
      <Field
        id="income-amount"
        label="Valor da renda (BRL)"
        hint="Use vírgula para centavos (ex.: 5.000,00). Valores com mais de duas casas não são aceitos."
        error={error}
      >
        <input
          inputMode="decimal"
          value={amountMask}
          onChange={(e) => setAmountMask(formatBRLInputMask(e.target.value))}
          placeholder="5.000,00"
          autoComplete="off"
        />
      </Field>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancelar</Button>
        <Button type="submit" busy={busy}>{busy ? "Salvando…" : "Salvar renda"}</Button>
      </div>
    </form>
  );
}
