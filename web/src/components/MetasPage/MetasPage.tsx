"use client";

import { useMemo, useState } from "react";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, type EnvelopeDTO } from "../../lib/api";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import { Dialog } from "../ui/Dialog";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm } from "../ExpenseForm/ExpenseForm";
import { EnvelopeCard } from "../EnvelopeCard/EnvelopeCard";
import { AppShell } from "../AppShell/AppShell";
import styles from "../VerbasPage/Verbas.module.css";

export function MetasPage({ email, onLogout }: { email: string; onLogout(): void }) {
  const { client } = useAuth();
  const { summary, loading, error, refresh } = useLedgerSummary();
  const [newGoal, setNewGoal] = useState(false);
  const [contribution, setContribution] = useState<EnvelopeDTO | null>(null);
  const [editingTarget, setEditingTarget] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const goals = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "GOAL"), [summary]);
  const targets = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "SAVINGS_TARGET"), [summary]);

  async function archive(envelope: EnvelopeDTO) {
    if (!confirm(`Encerrar "${envelope.name}"?`)) return;
    await createApiClient(client).archiveEnvelope(envelope.id);
    setFeedback(`Meta "${envelope.name}" encerrada.`);
    void refresh();
  }

  function contributionSaved(targetJustReached?: boolean) {
    setContribution(null);
    setFeedback(targetJustReached ? "Parabéns! Você alcançou esta meta pela primeira vez." : "Aporte registrado.");
    void refresh();
  }

  return (
    <AppShell current="goals" email={email} onLogout={onLogout}>
      <header className={styles.pageHeader}>
        <div><p className={styles.eyebrow}>Objetivos financeiros</p><h1>Metas</h1><p>Metas mensais de aporte e reservas para objetivos específicos, sem misturar suas regras.</p></div>
        <button type="button" className={styles.primaryAction} onClick={() => setNewGoal(true)}>Nova meta</button>
      </header>
      {feedback && <p className={styles.feedback} role="status">{feedback}</p>}
      {loading && <p className={styles.loadingState} role="status" aria-busy="true">Carregando metas…</p>}
      {error && <p className={styles.error} role="alert">{error} <button className={styles.textAction} type="button" onClick={() => void refresh()}>Tentar novamente</button></p>}
      {!loading && !error && <div className={styles.purposeGrid}>
        <GoalSection title="Metas de aporte" rule="Faça aportes de pelo menos o valor planejado a cada mês, como investimentos." envelopes={goals} onContribute={setContribution} onArchive={archive} />
        <GoalSection title="Metas de acumulação" rule="Junte qualquer valor até alcançar o alvo. O saldo acumulado não reinicia no próximo mês." envelopes={targets} onContribute={setContribution} onArchive={archive} onEditTarget={setEditingTarget} />
      </div>}
      <Dialog open={newGoal} onClose={() => setNewGoal(false)} title="Nova meta"><EnvelopeForm initialPurpose="SAVINGS_TARGET" onCancel={() => setNewGoal(false)} onSuccess={() => { setNewGoal(false); void refresh(); }} /></Dialog>
      <Dialog open={contribution !== null} onClose={() => setContribution(null)} title={contribution ? `Aportar em ${contribution.name}` : "Registrar aporte"}>{contribution && <ExpenseForm envelope={contribution} kind="CONTRIBUTION" onCancel={() => setContribution(null)} onSuccess={contributionSaved} />}</Dialog>
      <Dialog open={editingTarget !== null} onClose={() => setEditingTarget(null)} title={editingTarget ? `Editar alvo de ${editingTarget.name}` : "Editar alvo"}>{editingTarget && <TargetForm envelope={editingTarget} onCancel={() => setEditingTarget(null)} onSuccess={() => { setEditingTarget(null); setFeedback("Alvo atualizado."); void refresh(); }} />}</Dialog>
    </AppShell>
  );
}

function GoalSection({ title, rule, envelopes, onContribute, onArchive, onEditTarget }: { title: string; rule: string; envelopes: EnvelopeDTO[]; onContribute(envelope: EnvelopeDTO): void; onArchive(envelope: EnvelopeDTO): void; onEditTarget?: (envelope: EnvelopeDTO) => void }) {
  return <section className={styles.purposeSection} aria-label={title}><div className={styles.sectionHeader}><h2>{title}</h2><p>{rule}</p></div>{envelopes.length === 0 ? <p className={styles.emptyState} role="status">Nenhuma meta criada ainda.</p> : <ul className={styles.envelopeList}>{envelopes.map((envelope) => <EnvelopeCard key={envelope.id} envelope={envelope} onRegisterExpense={onContribute} onArchive={onArchive} onEditTarget={onEditTarget} variant="verbas" />)}</ul>}</section>;
}

function TargetForm({ envelope, onCancel, onSuccess }: { envelope: EnvelopeDTO; onCancel(): void; onSuccess(): void }) {
  const { client } = useAuth();
  const [amount, setAmount] = useState(formatBRLInputMask(envelope.targetAmount?.amount ?? ""));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const plain = maskToPlain(amount);
    if (!/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") { setError("Informe um alvo maior que zero."); return; }
    setBusy(true);
    try { await createApiClient(client).updateEnvelope(envelope.id, { targetAmount: { amount: plain, currency: "BRL" } }); onSuccess(); }
    catch { setError("Não foi possível atualizar o alvo."); } finally { setBusy(false); }
  }
  return <form className={styles.filtersBar} onSubmit={submit}>{error && <p role="alert" className={styles.error}>{error}</p>}<label htmlFor="target-amount">Valor total da meta (BRL)</label><input id="target-amount" inputMode="decimal" value={amount} onChange={(event) => setAmount(formatBRLInputMask(event.target.value))} required /><div><button type="button" onClick={onCancel} disabled={busy}>Cancelar</button><button type="submit" className={styles.primaryAction} disabled={busy}>{busy ? "Salvando…" : "Salvar alvo"}</button></div></form>;
}
