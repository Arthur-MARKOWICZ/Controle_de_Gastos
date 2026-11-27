"use client";

import { useEffect, useMemo, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { ApiError, createApiClient, type EnvelopeDTO } from "../../lib/api";
import { currentMonthSaoPaulo, todaySaoPaulo } from "../../lib/dates";
import { formatBRL, formatBRLInputMask, maskToPlain, subtractBRL } from "../../lib/money";
import { focusField } from "../../lib/form-errors";
import { isContributionPurpose } from "../EnvelopeCard/EnvelopeCard";
import { type ExpenseResult } from "../ExpenseForm/ExpenseForm";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import styles from "./QuickExpenseDialog.module.css";

export type QuickExpenseSuccess = ExpenseResult & {
  entryId: string;
  envelopeId: string;
  envelopeName: string;
  kind: "EXPENSE" | "CONTRIBUTION";
};

type Props = {
  open: boolean;
  onClose(): void;
  envelopes?: EnvelopeDTO[];
  initialEnvelopeId?: string;
  month?: string;
  onSuccess(result: QuickExpenseSuccess): void;
};

function yesterdayFrom(todayIso: string): string {
  const [y, m, d] = todayIso.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d - 1));
  return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, "0")}-${String(dt.getUTCDate()).padStart(2, "0")}`;
}

export function QuickExpenseDialog({
  open,
  onClose,
  envelopes,
  initialEnvelopeId,
  month,
  onSuccess,
}: Props) {
  return (
    <Dialog open={open} onClose={onClose} title="Lançamento rápido">
      {open && (
        <QuickExpenseForm
          envelopes={envelopes}
          initialEnvelopeId={initialEnvelopeId}
          month={month}
          onClose={onClose}
          onSuccess={onSuccess}
        />
      )}
    </Dialog>
  );
}

function QuickExpenseForm({
  envelopes: providedEnvelopes,
  initialEnvelopeId,
  month,
  onClose,
  onSuccess,
}: Omit<Props, "open">) {
  const { client } = useAuth();
  const api = useMemo(() => createApiClient(client), [client]);
  const [loadedEnvelopes, setLoadedEnvelopes] = useState<EnvelopeDTO[]>([]);
  const [loadingEnvelopes, setLoadingEnvelopes] = useState(!providedEnvelopes);
  const [loadError, setLoadError] = useState<string | null>(null);

  const today = todaySaoPaulo();
  const yesterday = yesterdayFrom(today);

  const envelopes = useMemo(
    () => (providedEnvelopes ?? loadedEnvelopes).filter((e) => !e.archivedAt),
    [providedEnvelopes, loadedEnvelopes],
  );

  const defaultSelectedId = useMemo(() => {
    if (initialEnvelopeId && envelopes.some((e) => e.id === initialEnvelopeId)) {
      return initialEnvelopeId;
    }
    return envelopes[0]?.id ?? "";
  }, [envelopes, initialEnvelopeId]);

  const [chosenId, setChosenId] = useState<string | null>(null);
  const selectedId = chosenId && envelopes.some((e) => e.id === chosenId) ? chosenId : defaultSelectedId;

  const [amountMask, setAmountMask] = useState("");
  const [occurredAt, setOccurredAt] = useState(today);
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<"envelope" | "amount" | "description", string>>>({});

  useEffect(() => {
    if (providedEnvelopes) return;
    let cancelled = false;
    api
      .getLedgerSummary(month ?? currentMonthSaoPaulo())
      .then((summary) => {
        if (!cancelled) setLoadedEnvelopes(summary.envelopes.filter((e) => !e.archivedAt));
      })
      .catch(() => {
        if (!cancelled) setLoadError("Não foi possível carregar suas verbas.");
      })
      .finally(() => {
        if (!cancelled) setLoadingEnvelopes(false);
      });
    return () => {
      cancelled = true;
    };
  }, [providedEnvelopes, month, api]);

  const selected = envelopes.find((e) => e.id === selectedId) ?? null;
  const kind: "EXPENSE" | "CONTRIBUTION" =
    selected && selected.role === "OWNER" && isContributionPurpose(selected.purpose)
      ? "CONTRIBUTION"
      : "EXPENSE";

  const balanceAfter =
    selected && kind === "EXPENSE"
      ? subtractBRL(selected.available.amount, maskToPlain(amountMask))
      : null;
  const overdrawn = balanceAfter?.startsWith("-") ?? false;

  function failField(field: "envelope" | "amount" | "description", id: string, message: string) {
    setFieldErrors({ [field]: message });
    focusField(id);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});

    if (!selected) {
      failField("envelope", "quick-expense-envelope", "Escolha uma verba para o lançamento");
      return;
    }
    const plain = maskToPlain(amountMask);
    if (!plain || !/^\d{1,17}\.\d{2}$/.test(plain)) {
      failField("amount", "quick-expense-amount", "Valor deve ter duas casas decimais");
      return;
    }
    if (plain === "0.00") {
      failField("amount", "quick-expense-amount", "Valor deve ser positivo");
      return;
    }
    if (description.length > 140) {
      failField("description", "quick-expense-desc", "Descrição até 140 caracteres");
      return;
    }

    setBusy(true);
    try {
      const entry = await api.createEntry(selected.id, {
        kind,
        amount: { amount: plain, currency: "BRL" },
        occurredAt,
        description: description.trim() || null,
      });
      onSuccess({
        entryId: entry.id,
        envelopeId: selected.id,
        envelopeName: selected.name,
        kind,
        targetJustReached: entry.targetJustReached ?? false,
        balanceAfter,
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        failField("amount", "quick-expense-amount", err.detail || "Valor inválido para este lançamento");
      } else if (err instanceof ApiError) {
        setError(err.detail || `Não foi possível registrar o ${kind === "EXPENSE" ? "gasto" : "aporte"}`);
      } else {
        setError("Erro inesperado. Revise os dados e tente registrar de novo.");
      }
    } finally {
      setBusy(false);
    }
  }

  if (loadingEnvelopes) {
    return <p role="status" className={styles.loading}>Carregando verbas…</p>;
  }
  if (loadError) {
    return <Notice tone="danger">{loadError}</Notice>;
  }
  if (envelopes.length === 0) {
    return (
      <div className={styles.empty}>
        <p>Nenhuma verba ativa neste mês. Crie uma verba antes de lançar um gasto.</p>
        <Button variant="secondary" onClick={onClose}>Fechar</Button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className={styles.form}>
      {error && <Notice tone="danger">{error}</Notice>}

      <div className={styles.chipGroup} role="group" aria-label="Atalho de verbas">
        {envelopes.slice(0, 4).map((env) => (
          <button
            key={env.id}
            type="button"
            className={env.id === selectedId ? `${styles.chip} ${styles.chipActive}` : styles.chip}
            aria-pressed={env.id === selectedId}
            onClick={() => setChosenId(env.id)}
          >
            <span>{env.name}</span>
            <small>{formatBRL(env.available.amount)}</small>
          </button>
        ))}
      </div>

      <Field id="quick-expense-envelope" label="Verba" error={fieldErrors.envelope}>
        <select
          value={selectedId}
          onChange={(event) => setChosenId(event.target.value)}
          required
        >
          {envelopes.map((env) => (
            <option key={env.id} value={env.id}>
              {env.name} ({formatBRL(env.available.amount)})
            </option>
          ))}
        </select>
      </Field>

      <Field
        id="quick-expense-amount"
        label="Valor (BRL)"
        hint="Use vírgula para centavos (ex.: 45,90)."
        error={fieldErrors.amount}
      >
        <input
          inputMode="decimal"
          value={amountMask}
          onChange={(event) => setAmountMask(formatBRLInputMask(event.target.value))}
          placeholder="0,00"
          required
          autoFocus
        />
      </Field>

      {kind === "EXPENSE" && balanceAfter && (
        <div aria-live="polite" className={overdrawn ? styles.previewNegative : styles.preview}>
          <p>Saldo após o gasto: <strong>{formatBRL(balanceAfter)}</strong></p>
          {overdrawn && <p>A verba ficará com saldo negativo. O registro continua permitido.</p>}
        </div>
      )}

      <div className={styles.dateRow}>
        <Field id="quick-expense-date" label="Data">
          <input
            type="date"
            value={occurredAt}
            onChange={(event) => setOccurredAt(event.target.value)}
            required
            max={today}
          />
        </Field>
        <div className={styles.dateShortcuts} role="group" aria-label="Atalhos de data">
          <button
            type="button"
            className={occurredAt === today ? `${styles.dateChip} ${styles.dateChipActive}` : styles.dateChip}
            onClick={() => setOccurredAt(today)}
          >
            Hoje
          </button>
          <button
            type="button"
            className={occurredAt === yesterday ? `${styles.dateChip} ${styles.dateChipActive}` : styles.dateChip}
            onClick={() => setOccurredAt(yesterday)}
          >
            Ontem
          </button>
        </div>
      </div>

      <Field id="quick-expense-desc" label="Descrição (opcional)" error={fieldErrors.description}>
        <input
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          maxLength={140}
          placeholder={kind === "EXPENSE" ? "Ex: Padaria, Mercado" : "Ex: Aporte do mês"}
        />
      </Field>

      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose} disabled={busy}>Cancelar</Button>
        <Button type="submit" busy={busy}>
          {busy ? "Registrando…" : kind === "EXPENSE" ? "Registrar gasto" : "Registrar aporte"}
        </Button>
      </div>
    </form>
  );
}
