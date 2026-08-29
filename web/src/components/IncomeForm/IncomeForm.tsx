"use client";

import { useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { ApiError, createApiClient } from "../../lib/api";
import { formatBRL, formatBRLInputMask, maskToPlain } from "../../lib/money";
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
    if (plain === "0.00") {
      // 0 is allowed per backend (non-negative), but show help
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
          setError("Valor inválido. Use formato 5000,00 com duas casas, sem arredondamento (ex: 10.999 é inválido)");
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
      <h3 id="income-form-title" style={{ fontFamily: "Georgia, serif", fontWeight: 500, fontSize: "1.125rem", marginBottom: "0.25rem" }}>Configurar renda mensal</h3>
      <p style={{ fontSize: "0.8125rem", color: "var(--foreground-muted)", marginBottom: "0.75rem" }}>
        Vigência é o mês corrente em <code>America/Sao_Paulo</code>. Repetir o mesmo valor é idempotente e não cria nova revisão.
      </p>
      {error && <p role="alert" className={styles.error}>{error}</p>}
      <label htmlFor="income-amount">Valor da renda (BRL)</label>
      <input
        id="income-amount"
        inputMode="decimal"
        value={amountMask}
        onChange={(e) => setAmountMask(formatBRLInputMask(e.target.value))}
        placeholder="5.000,00"
        aria-describedby="income-help"
        autoComplete="off"
      />
      <span id="income-help" className={styles.help}>Digite com vírgula. Será enviado como 5000.00 com duas casas. Ex: 10.999 é rejeitado (sem arredondamento).</span>
      <div className={styles.actions}>
        <button type="button" onClick={onCancel} disabled={busy}>Cancelar</button>
        <button type="submit" disabled={busy} className={styles.primary}>{busy ? "Salvando…" : "Salvar renda"}</button>
      </div>
    </form>
  );
}
