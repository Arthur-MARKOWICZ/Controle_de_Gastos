import { useEffect, useRef, useState } from "react";
import { WarningCircle } from "@phosphor-icons/react";
import { Field } from "./Field";
import { Amount } from "./Amount";
import { registerExpense } from "../lib/api";
import { IpcError } from "../lib/ipc";
import { todaySaoPaulo } from "../lib/dates";
import { maskPtBR, subtractBRL, toPlainAmount } from "../lib/money";
import type { SummaryEnvelope } from "../lib/types";
import styles from "./ExpenseDialog.module.css";

const MAX_DESCRICAO = 140;
const ERRO_VALOR = "Informe um valor em reais com duas casas decimais, maior que zero.";

export type ExpenseDialogProps = {
  envelope: SummaryEnvelope;
  onClose: () => void;
  onRegistered: () => void;
  /** Injetável no teste; por padrão, hoje em America/Sao_Paulo. */
  today?: string;
};

export function ExpenseDialog({
  envelope,
  onClose,
  onRegistered,
  today,
}: ExpenseDialogProps) {
  const [masked, setMasked] = useState("");
  const [description, setDescription] = useState("");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const summaryRef = useRef<HTMLDivElement>(null);
  // Captura quem abriu o diálogo no primeiro render, antes do `autoFocus` do
  // campo valor mover o foco — num efeito já seria tarde demais.
  const [opener] = useState<Element | null>(() => document.activeElement);

  const occurredAt = today ?? todaySaoPaulo();

  // Devolve o foco ao gatilho ao fechar — vale tanto para a ação da verba
  // quanto para o atalho de teclado.
  useEffect(() => {
    return () => {
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, [opener]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  const plain = toPlainAmount(masked);
  // Projeção só de apresentação: a API decide o saldo real.
  const projected = plain ? subtractBRL(envelope.available.amount, plain) : null;
  const projectedNegative = projected !== null && projected.startsWith("-");

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (pending) return;
    setSummaryError(null);

    if (!plain) {
      // Requisição não sai: o erro fica no campo e no resumo do topo.
      setAmountError(ERRO_VALOR);
      setSummaryError(ERRO_VALOR);
      requestAnimationFrame(() => summaryRef.current?.focus());
      return;
    }
    setAmountError(null);

    setPending(true);
    try {
      await registerExpense(envelope.id, {
        amount: plain,
        occurredAt,
        description: description.trim() === "" ? null : description.trim(),
      });
      onRegistered();
      onClose();
    } catch (raw) {
      // Recusa da API (403 em verba de terceiro, por exemplo): nunca afirma
      // que o gasto foi lançado.
      const message =
        raw instanceof IpcError || raw instanceof Error
          ? raw.message
          : "Não foi possível registrar o gasto.";
      setSummaryError(message);
      requestAnimationFrame(() => summaryRef.current?.focus());
    } finally {
      setPending(false);
    }
  }

  return (
    <div className={styles.backdrop}>
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-gasto"
      >
        <h2 className={styles.title} id="titulo-gasto">
          Registrar gasto
        </h2>
        <p className={styles.envelope}>
          Verba {envelope.name} · saldo disponível{" "}
          <Amount plain={envelope.available.amount} />
        </p>

        {summaryError ? (
          <div
            className={styles.summary}
            role="alert"
            tabIndex={-1}
            ref={summaryRef}
            data-testid="resumo-de-erro"
          >
            {summaryError}
          </div>
        ) : null}

        <form className={styles.form} onSubmit={submit} noValidate>
          <Field
            id="valor"
            label="Valor"
            value={masked}
            onChange={(next) => setMasked(maskPtBR(next))}
            inputMode="decimal"
            hint="Em reais, com centavos. Exemplo: 45,90."
            error={amountError}
            autoFocus
            disabled={pending}
          />
          <Field
            id="data"
            label="Data"
            type="date"
            value={occurredAt}
            onChange={() => {
              /* o corte registra na data de hoje */
            }}
            hint="Hoje, no calendário de São Paulo."
            disabled
          />
          <Field
            id="descricao"
            label="Descrição (opcional)"
            value={description}
            onChange={setDescription}
            maxLength={MAX_DESCRICAO}
            hint={`Até ${MAX_DESCRICAO} caracteres.`}
            disabled={pending}
          />

          {projected !== null ? (
            <p
              className={`${styles.projection} ${
                projectedNegative ? styles.projectionNegative : ""
              }`}
              data-testid="saldo-projetado"
            >
              {projectedNegative ? (
                <WarningCircle size={20} aria-hidden />
              ) : null}
              <span>
                Saldo depois deste gasto: <Amount plain={projected} />
                {projectedNegative
                  ? " — a verba fica negativa. O registro é permitido."
                  : ""}
              </span>
            </p>
          ) : null}

          <div className={styles.actions}>
            <button
              className={styles.ghost}
              type="button"
              onClick={onClose}
              disabled={pending}
            >
              Cancelar
            </button>
            <button className={styles.primary} type="submit" disabled={pending}>
              {pending ? "Registrando…" : "Registrar gasto"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
