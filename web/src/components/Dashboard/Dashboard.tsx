"use client";

import { useState } from "react";
import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";
import styles from "./Dashboard.module.css";
import { useEnvelopes } from "../../hooks/useEnvelopes";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import { useIncome, useIncomeHistory } from "../../hooks/useIncome";
import { currentMonthSaoPaulo, formatMonthLabel, parseMonthParam } from "../../lib/dates";
import { BalanceSummary } from "../BalanceSummary/BalanceSummary";
import { IncomeCard } from "../IncomeCard/IncomeCard";
import { IncomeForm } from "../IncomeForm/IncomeForm";
import { IncomeHistory } from "../IncomeHistory/IncomeHistory";
import { EnvelopeCard } from "../EnvelopeCard/EnvelopeCard";
import { Dialog } from "../ui/Dialog";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm } from "../ExpenseForm/ExpenseForm";
import type { EnvelopeDTO } from "../../lib/api";
import { createApiClient } from "../../lib/api";
import { useAuth } from "../../auth/auth-context";

export function Dashboard({ email, onLogout }: { email: string; onLogout(): void }) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { client } = useAuth();
  const rawMonth = parseMonthParam(searchParams.get("month"));
  const month = rawMonth ?? currentMonthSaoPaulo();
  const monthLabel = formatMonthLabel(month);

  const { envelopes, loading: envelopesLoading, error: envelopesError, refresh: refreshEnvelopes } = useEnvelopes(month);
  const { summary, loading: summaryLoading, error: summaryError, refresh: refreshSummary } = useLedgerSummary(month);
  const { income, loading: incomeLoading, error: incomeError, notConfigured, refresh: refreshIncome } = useIncome(month);
  const { data: history, loading: historyLoading, error: historyError, load: loadHistory, refresh: refreshHistory } = useIncomeHistory(0, 20);

  const [showNewEnvelope, setShowNewEnvelope] = useState(false);
  const [showIncomeForm, setShowIncomeForm] = useState(false);
  const [expenseTarget, setExpenseTarget] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const loading = envelopesLoading || summaryLoading || incomeLoading;
  const error = envelopesError || summaryError;

  function onMonthChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value;
    const url = v ? `/?month=${v}` : "/";
    router.push(url);
  }

  async function handleArchive(envelope: EnvelopeDTO) {
    if (!confirm(`Arquivar "${envelope.name}"?`)) return;
    try {
      await createApiClient(client).archiveEnvelope(envelope.id);
      setFeedback(`Verba "${envelope.name}" arquivada`);
      void refreshEnvelopes();
      void refreshSummary();
    } catch {
      setFeedback("Não foi possível arquivar");
    }
  }

  function handleEnvelopeSuccess() {
    setShowNewEnvelope(false);
    setFeedback("Verba criada com sucesso");
    void refreshEnvelopes();
    void refreshSummary();
  }

  function handleExpenseSuccess() {
    const name = expenseTarget?.name ?? "verba";
    setExpenseTarget(null);
    setFeedback(`Gasto registrado em "${name}"`);
    void refreshEnvelopes();
    void refreshSummary();
  }

  function handleIncomeSuccess() {
    setShowIncomeForm(false);
    setFeedback("Renda salva com sucesso");
    void refreshIncome();
    void refreshSummary();
    void refreshHistory();
  }

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar} aria-label="Navegação principal">
        <a className={styles.brand} href="#conteudo"><span aria-hidden="true" className={styles.brandMark}>V</span><span>Verbas</span></a>
        <nav><ul className={styles.navList}>
          <li><Link href="/" aria-current="page">Visão geral</Link></li><li><Link href="/verbas">Verbas</Link></li><li><a href="#historico">Histórico</a></li><li><a href="#relatorios">Relatórios</a></li>
        </ul></nav>
        <div className={styles.sidebarFooter}>
          <span className={styles.avatar} aria-hidden="true">{email.slice(0, 1).toUpperCase()}</span>
          <span><strong>{email}</strong><button type="button" onClick={onLogout}>Sair da conta</button></span>
        </div>
      </aside>
      <main id="conteudo" className={styles.main}>
        <p className={styles.demoNotice} role="status" style={{ display: feedback ? "block" : "none" }}>{feedback ?? ""}</p>
        <header className={styles.pageHeader}>
          <div>
            <p className={styles.eyebrow}>{monthLabel}</p>
            <h1>Visão geral</h1>
            <p>Veja o que já está reservado antes de decidir o próximo gasto.</p>
            <div style={{ marginTop: "0.75rem", display: "flex", gap: "0.5rem", alignItems: "center" }}>
              <label htmlFor="month-picker" style={{ fontSize: "0.8125rem", color: "var(--foreground-muted)", fontWeight: 650 }}>Mês</label>
              <input id="month-picker" type="month" value={month} onChange={onMonthChange} style={{ padding: "0.375rem 0.5rem", border: "1px solid var(--border)", borderRadius: "0.375rem" }} />
            </div>
          </div>
          <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
            <button type="button" className={styles.primaryAction} onClick={() => setShowNewEnvelope(true)}>Nova verba</button>
            <button type="button" className={styles.secondaryAction} style={{ margin: 0 }} onClick={() => setShowIncomeForm(true)} aria-label="Configurar renda">Configurar renda</button>
          </div>
        </header>

        {loading && <p role="status" aria-busy="true">Carregando dados financeiros…</p>}
        {error && <p role="alert" style={{ color: "#7a2a2a", background: "#fdf0f0", border: "1px solid #d9a0a0", padding: "0.75rem", borderRadius: "0.375rem" }}>{error} <button type="button" onClick={() => { void refreshEnvelopes(); void refreshSummary(); void refreshIncome(); }} style={{ marginLeft: "0.5rem", color: "var(--accent)", fontWeight: 700, border: 0, background: "transparent", cursor: "pointer" }}>Tentar novamente</button></p>}

        {!loading && (
          <>
            <IncomeCard income={income} month={month} loading={incomeLoading} error={incomeError} onEdit={() => setShowIncomeForm(true)} />
            {notConfigured && !incomeLoading && (
              <p role="status" style={{ marginTop: "0.75rem", padding: "0.75rem", border: "1px solid var(--border)", background: "var(--surface-muted)", borderRadius: "0.375rem", fontSize: "0.8125rem", color: "var(--foreground-muted)" }}>
                Nenhuma renda configurada para {monthLabel}. Clique em <strong>Configurar renda</strong> para definir a renda de {month} (vigência em America/Sao_Paulo).
              </p>
            )}
            <div style={{ height: "1.5rem" }} aria-hidden="true" />
            <BalanceSummary summary={summary} monthLabel={monthLabel} />
          </>
        )}

        <div className={styles.contentGrid}>
          <section id="verbas" className={styles.panel} aria-labelledby="verbas-titulo">
            <div className={styles.panelHeader}><div><h2 id="verbas-titulo">Suas verbas</h2><p>Valores disponíveis neste mês</p></div><Link href="/verbas" style={{ fontSize: "0.8125rem", color: "var(--accent)", fontWeight: 700 }}>Ver todas →</Link></div>
            {envelopes.length === 0 && !loading && !error ? (
              <div style={{ padding: "2rem 1.5rem", textAlign: "center" }}>
                <p style={{ color: "var(--foreground-muted)", marginBottom: "1rem" }}>Nenhuma verba neste mês — crie a primeira para começar a reservar.</p>
                <button type="button" className={styles.primaryAction} onClick={() => setShowNewEnvelope(true)}>Criar primeira verba</button>
              </div>
            ) : (
              <ul className={styles.envelopeList}>
                {(summary?.envelopes ?? envelopes).slice(0, 4).map((envelope) => (
                  <EnvelopeCard key={envelope.id} envelope={envelope} onRegisterExpense={setExpenseTarget} onArchive={handleArchive} />
                ))}
              </ul>
            )}
          </section>
          <section id="historico" className={styles.panel} aria-labelledby="historico-titulo">
            <div className={styles.panelHeader}><div><h2 id="historico-titulo">Histórico de renda</h2><p>Alterações reais, mais recente primeiro</p></div><button type="button" onClick={() => void refreshHistory()} style={{ fontSize: "0.8125rem", color: "var(--accent)", fontWeight: 700, border: 0, background: "transparent", cursor: "pointer" }}>Atualizar</button></div>
            <IncomeHistory
              data={history}
              loading={historyLoading}
              error={historyError}
              onLoadMore={() => loadHistory((history?.page ?? 0) + 1)}
              onRetry={() => void refreshHistory()}
            />
            <div style={{ padding: "1rem 1.5rem", borderTop: "1px solid var(--border)", display: "grid", gap: "0.75rem" }}>
              <button type="button" className={styles.secondaryAction} style={{ margin: 0 }} onClick={() => setShowNewEnvelope(true)}>Nova verba</button>
              <p style={{ fontSize: "0.8125rem", color: "var(--foreground-muted)" }}>
                {summary?.income ? "Registre gastos nas verbas ao lado. Saldo negativo gera alerta, não bloqueio." : "Configure sua renda para habilitar o cálculo de não alocado."}
              </p>
            </div>
          </section>
        </div>

        <Dialog open={showNewEnvelope} onClose={() => setShowNewEnvelope(false)} title="Nova verba">
          <EnvelopeForm onSuccess={handleEnvelopeSuccess} onCancel={() => setShowNewEnvelope(false)} />
        </Dialog>

        <Dialog open={showIncomeForm} onClose={() => setShowIncomeForm(false)} title="Renda mensal">
          <IncomeForm initialAmount={income?.amount} onSuccess={handleIncomeSuccess} onCancel={() => setShowIncomeForm(false)} />
        </Dialog>

        <Dialog open={!!expenseTarget} onClose={() => setExpenseTarget(null)} title={expenseTarget ? `Registrar gasto em ${expenseTarget.name}` : "Registrar gasto"}>
          {expenseTarget && <ExpenseForm envelope={expenseTarget} onSuccess={handleExpenseSuccess} onCancel={() => setExpenseTarget(null)} />}
        </Dialog>
      </main>
    </div>
  );
}
