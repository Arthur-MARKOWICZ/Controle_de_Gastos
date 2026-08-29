"use client";

import { formatBRL } from "../../lib/money";
import type { EnvelopeDTO } from "../../lib/api";
import { EnvelopeCard } from "../EnvelopeCard/EnvelopeCard";
import styles from "./Verbas.module.css";

type Purpose = "LIMIT" | "GOAL" | "FIXED";

const PURPOSE_META: Record<Purpose, { label: string; hint: string }> = {
  LIMIT: { label: "Limite de gasto", hint: "Controle do que pode gastar" },
  GOAL: { label: "Meta de aporte", hint: "Quanto falta para atingir" },
  FIXED: { label: "Compromisso fixo", hint: "Contas que não podem falhar" },
};

type Props = {
  purpose: Purpose;
  envelopes: EnvelopeDTO[];
  onRegisterExpense: (e: EnvelopeDTO) => void;
  onArchive: (e: EnvelopeDTO) => void;
  onCreate: (purpose: Purpose) => void;
};

export function PurposeSection({ purpose, envelopes, onRegisterExpense, onArchive, onCreate }: Props) {
  const meta = PURPOSE_META[purpose];
  const totalBase = envelopes.reduce((acc, e) => acc + Number(e.baseAmount.amount), 0);
  const totalAvailable = envelopes.reduce((acc, e) => acc + Number(e.available.amount), 0);
  const count = envelopes.length;

  return (
    <section className={styles.purposeSection} data-purpose={purpose} aria-labelledby={`titulo-${purpose.toLowerCase()}`}>
      <div className={styles.sectionHeader}>
        <h2 id={`titulo-${purpose.toLowerCase()}`}>
          <i data-purpose={purpose} aria-hidden="true" />
          {meta.label}
        </h2>
        <p>{meta.hint}</p>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "end", marginTop: "0.5rem" }}>
          <div>
            <strong>{count === 0 ? "—" : formatBRL(totalAvailable.toFixed(2))}</strong>
            <p>{count === 0 ? "Nenhuma verba" : `de ${formatBRL(totalBase.toFixed(2))} · ${count} ${count === 1 ? "verba" : "verbas"}`}</p>
          </div>
          <span style={{ fontSize: "0.75rem", color: "var(--foreground-muted)" }}>{envelopes.filter(e => e.isNegative).length > 0 ? `⚠ ${envelopes.filter(e => e.isNegative).length} negativa(s)` : ""}</span>
        </div>
      </div>

      {envelopes.length === 0 ? (
        <div className={styles.emptyState} role="status">
          <p>Nenhuma verba de {meta.label.toLowerCase()} neste mês.</p>
          <button type="button" onClick={() => onCreate(purpose)}>Criar {meta.label.toLowerCase()}</button>
        </div>
      ) : (
        <ul className={styles.envelopeList}>
          {envelopes.map((env) => (
            <EnvelopeCard key={env.id} envelope={env} onRegisterExpense={onRegisterExpense} onArchive={onArchive} variant="verbas" />
          ))}
        </ul>
      )}
    </section>
  );
}
