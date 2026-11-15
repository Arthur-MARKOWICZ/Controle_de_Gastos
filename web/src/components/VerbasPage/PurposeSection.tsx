"use client";

import { formatBRL } from "../../lib/money";
import type { EnvelopeDTO } from "../../lib/api";
import { EnvelopeCard } from "../EnvelopeCard/EnvelopeCard";
import { Button } from "../ui/Button";
import styles from "./Verbas.module.css";

type Purpose = "LIMIT" | "FIXED";

const PURPOSE_META: Record<Purpose, { label: string; hint: string }> = {
  LIMIT: { label: "Limite de gasto", hint: "Controle do que pode gastar" },
  FIXED: { label: "Compromisso fixo", hint: "Contas que não podem falhar" },
};

type Props = {
  purpose: Purpose;
  envelopes: EnvelopeDTO[];
  onRegisterExpense: (e: EnvelopeDTO) => void;
  onManageParticipants: (e: EnvelopeDTO) => void;
  onManageRecurring?: (e: EnvelopeDTO) => void;
  onArchive: (e: EnvelopeDTO) => void;
  onToggleWealthAllocation: (e: EnvelopeDTO) => void;
  onCreate: (purpose: Purpose) => void;
};

export function PurposeSection({
  purpose,
  envelopes,
  onRegisterExpense,
  onManageParticipants,
  onManageRecurring,
  onArchive,
  onToggleWealthAllocation,
  onCreate,
}: Props) {
  const meta = PURPOSE_META[purpose];
  const totalBase = envelopes.reduce((acc, e) => acc + Number(e.baseAmount.amount), 0);
  const totalAvailable = envelopes.reduce((acc, e) => acc + Number(e.available.amount), 0);
  const count = envelopes.length;
  const negativeCount = envelopes.filter(e => e.isNegative).length;

  return (
    <section className={styles.purposeSection} data-purpose={purpose} aria-labelledby={`titulo-${purpose.toLowerCase()}`}>
      <div className={styles.sectionHeader}>
        <h2 id={`titulo-${purpose.toLowerCase()}`}>
          <i data-purpose={purpose} aria-hidden="true" />
          {meta.label}
        </h2>
        <p>{meta.hint}</p>
        <div className={styles.sectionTotals}>
          <div>
            <strong>{count === 0 ? "—" : formatBRL(totalAvailable.toFixed(2))}</strong>
            <p>{count === 0 ? "Nenhuma verba" : `de ${formatBRL(totalBase.toFixed(2))} · ${count} ${count === 1 ? "verba" : "verbas"}`}</p>
          </div>
          <span className={styles.negativeCount}>
            {negativeCount > 0 ? `${negativeCount} negativa(s)` : ""}
          </span>
          <Button variant="secondary" onClick={() => onCreate(purpose)}>
            Criar {meta.label.toLowerCase()}
          </Button>
        </div>
      </div>

      {envelopes.length === 0 ? (
        <div className={styles.emptyState} role="status">
          <p>Nenhuma verba de {meta.label.toLowerCase()} neste mês.</p>
        </div>
      ) : (
        <ul className={styles.envelopeList}>
          {envelopes.map((env) => (
            <EnvelopeCard
              key={env.id}
              envelope={env}
              onRegisterExpense={onRegisterExpense}
              onManageParticipants={onManageParticipants}
              onManageRecurring={onManageRecurring}
              onArchive={onArchive}
              onToggleWealthAllocation={onToggleWealthAllocation}
            />
          ))}
        </ul>
      )}
    </section>
  );
}
