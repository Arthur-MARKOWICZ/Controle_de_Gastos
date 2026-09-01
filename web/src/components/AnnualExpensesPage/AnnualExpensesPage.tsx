"use client";

import { useMemo, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import type { EnvelopeDTO } from "../../lib/api";
import { createApiClient } from "../../lib/api";
import { AppShell } from "../AppShell/AppShell";
import { Dialog } from "../ui/Dialog";
import { EnvelopeCard } from "../EnvelopeCard/EnvelopeCard";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm } from "../ExpenseForm/ExpenseForm";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import styles from "../VerbasPage/Verbas.module.css";

export function AnnualExpensesPage({ email, onLogout }: { email: string; onLogout(): void }) {
  const { summary, loading, error, refresh } = useLedgerSummary();
  const { client } = useAuth();
  const [creating, setCreating] = useState(false);
  const [expense, setExpense] = useState<EnvelopeDTO | null>(null);
  const [editing, setEditing] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const annual = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "ANNUAL_EXPENSE"), [summary]);

  async function archive(envelope: EnvelopeDTO) {
    if (!confirm(`Arquivar "${envelope.name}"?`)) return;
    await createApiClient(client).archiveEnvelope(envelope.id);
    setFeedback(`Gasto anual "${envelope.name}" arquivado.`);
    void refresh();
  }

  return <AppShell current="annual" email={email} onLogout={onLogout}>
    <header className={styles.pageHeader}>
      <div><p className={styles.eyebrow}>Despesas recorrentes</p><h1>Gastos anuais</h1><p>Planeje IPVA e assinaturas anuais. O pagamento real continua sendo registrado por você.</p></div>
      <button type="button" className={styles.primaryAction} onClick={() => setCreating(true)}>Novo gasto anual</button>
    </header>
    {feedback && <p className={styles.feedback} role="status">{feedback}</p>}
    {loading && <p className={styles.loadingState} role="status" aria-busy="true">Carregando gastos anuais…</p>}
    {error && <p className={styles.error} role="alert">{error} <button className={styles.textAction} type="button" onClick={() => void refresh()}>Tentar novamente</button></p>}
    {!loading && !error && <section className={styles.purposeSection} aria-label="Gastos anuais">
      <div className={styles.sectionHeader}><h2>Próximos vencimentos</h2><p>Uma parcela mensal acumula até a data escolhida; valores não reservados não desaparecem na virada do mês.</p></div>
      {annual.length === 0 ? <p className={styles.emptyState} role="status">Nenhum gasto anual criado ainda.</p> : <ul className={styles.envelopeList}>{annual.map((envelope) => <EnvelopeCard key={envelope.id} envelope={envelope} onRegisterExpense={setExpense} onArchive={archive} onEditAnnual={setEditing} variant="verbas" />)}</ul>}
    </section>}
    <Dialog open={creating} onClose={() => setCreating(false)} title="Novo gasto anual"><EnvelopeForm initialPurpose="ANNUAL_EXPENSE" onCancel={() => setCreating(false)} onSuccess={() => { setCreating(false); setFeedback("Gasto anual criado."); void refresh(); }} /></Dialog>
    <Dialog open={expense !== null} onClose={() => setExpense(null)} title={expense ? `Registrar gasto em ${expense.name}` : "Registrar gasto"}>{expense && <ExpenseForm envelope={expense} onCancel={() => setExpense(null)} onSuccess={() => { setExpense(null); setFeedback("Gasto registrado."); void refresh(); }} />}</Dialog>
    <Dialog open={editing !== null} onClose={() => setEditing(null)} title={editing ? `Editar ${editing.name}` : "Editar gasto anual"}>{editing && <AnnualExpenseEditForm envelope={editing} onCancel={() => setEditing(null)} onSuccess={() => { setEditing(null); setFeedback("Gasto anual atualizado."); void refresh(); }} />}</Dialog>
  </AppShell>;
}

function AnnualExpenseEditForm({ envelope, onCancel, onSuccess }: { envelope: EnvelopeDTO; onCancel(): void; onSuccess(): void }) {
  const { client } = useAuth();
  const annual = envelope.annualExpense!;
  const [amount, setAmount] = useState(formatBRLInputMask(annual.annualAmount.amount));
  const [dueMonth, setDueMonth] = useState(String(annual.dueMonth));
  const [dueDay, setDueDay] = useState(String(annual.dueDay));
  const [fundingMode, setFundingMode] = useState(annual.fundingMode);
  const [error, setError] = useState<string | null>(null);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const plain = maskToPlain(amount);
    if (!/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") { setError("Informe um valor anual positivo."); return; }
    try { await createApiClient(client).updateEnvelope(envelope.id, { annualAmount: { amount: plain, currency: "BRL" }, dueMonth: Number(dueMonth), dueDay: Number(dueDay), fundingMode }); onSuccess(); }
    catch { setError("Não foi possível atualizar o gasto anual."); }
  }
  return <form className={styles.filtersBar} onSubmit={submit}>{error && <p role="alert" className={styles.error}>{error}</p>}<label htmlFor="annual-edit-amount">Valor anual (BRL)</label><input id="annual-edit-amount" inputMode="decimal" value={amount} onChange={event => setAmount(formatBRLInputMask(event.target.value))} required /><label htmlFor="annual-edit-month">Mês</label><input id="annual-edit-month" type="number" min="1" max="12" value={dueMonth} onChange={event => setDueMonth(event.target.value)} required /><label htmlFor="annual-edit-day">Dia</label><input id="annual-edit-day" type="number" min="1" max="31" value={dueDay} onChange={event => setDueDay(event.target.value)} required /><label htmlFor="annual-edit-mode">Reserva</label><select id="annual-edit-mode" value={fundingMode} onChange={event => setFundingMode(event.target.value as "MONTHLY" | "ONE_TIME")}><option value="MONTHLY">Mensal</option><option value="ONE_TIME">Pagamento único</option></select><div><button type="button" onClick={onCancel}>Cancelar</button><button type="submit" className={styles.primaryAction}>Salvar</button></div></form>;
}
