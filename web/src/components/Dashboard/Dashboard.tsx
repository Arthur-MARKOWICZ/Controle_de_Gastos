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
import { EnvelopeCard, isContributionPurpose } from "../EnvelopeCard/EnvelopeCard";
import { Dialog } from "../ui/Dialog";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Button } from "../ui/Button";
import { Notice } from "../ui/Notice";
import { PageHeader } from "../ui/PageHeader";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm, overdraftNotice, type ExpenseResult } from "../ExpenseForm/ExpenseForm";
import { Glossary } from "../Glossary/Glossary";
import type { EnvelopeDTO } from "../../lib/api";
import { createApiClient } from "../../lib/api";
import { useAuth } from "../../auth/auth-context";
import { AppShell } from "../AppShell/AppShell";

const ARCHIVE_MESSAGE =
  "A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no histórico.";

export function Dashboard({ email, onLogout }: { email: string; onLogout(): void }) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { client } = useAuth();
  const rawMonth = parseMonthParam(searchParams.get("month"));
  const month = rawMonth ?? currentMonthSaoPaulo();
  const monthLabel = formatMonthLabel(month);

  const {
    envelopes,
    loading: envelopesLoading,
    busy: envelopesBusy,
    error: envelopesError,
    refresh: refreshEnvelopes,
  } = useEnvelopes(month);
  const {
    summary,
    loading: summaryLoading,
    busy: summaryBusy,
    error: summaryError,
    refresh: refreshSummary,
  } = useLedgerSummary(month);
  const {
    income,
    loading: incomeLoading,
    busy: incomeBusy,
    error: incomeError,
    notConfigured,
    refresh: refreshIncome,
  } = useIncome(month);
  const {
    data: history,
    loading: historyLoading,
    busy: historyBusy,
    error: historyError,
    load: loadHistory,
    refresh: refreshHistory,
  } = useIncomeHistory(0, 20);

  const [showNewEnvelope, setShowNewEnvelope] = useState(false);
  const [showIncomeForm, setShowIncomeForm] = useState(false);
  const [expenseTarget, setExpenseTarget] = useState<EnvelopeDTO | null>(null);
  const [showGlossary, setShowGlossary] = useState(false);
  const [pendingArchive, setPendingArchive] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const loading = envelopesLoading || summaryLoading || incomeLoading;
  const busy = envelopesBusy || summaryBusy || incomeBusy || historyBusy;
  const error = envelopesError || summaryError;

  function clearFeedback() {
    setFeedback(null);
  }

  function onMonthChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value;
    const url = v ? `/?month=${v}` : "/";
    router.push(url);
  }

  async function confirmArchive() {
    if (!pendingArchive) return;
    const envelope = pendingArchive;
    setPendingArchive(null);
    try {
      await createApiClient(client).archiveEnvelope(envelope.id);
      setFeedback(`Verba "${envelope.name}" encerrada`);
      void refreshEnvelopes();
      void refreshSummary();
    } catch {
      setFeedback("Não foi possível encerrar a verba");
    }
  }

  function handleEnvelopeSuccess() {
    setShowNewEnvelope(false);
    setFeedback("Verba criada com sucesso");
    void refreshEnvelopes();
    void refreshSummary();
  }

  const expenseKind = expenseTarget && isContributionPurpose(expenseTarget.purpose) ? "CONTRIBUTION" : "EXPENSE";

  function handleExpenseSuccess(result: ExpenseResult) {
    const name = expenseTarget?.name ?? "verba";
    setExpenseTarget(null);
    setFeedback(`${expenseKind === "CONTRIBUTION" ? "Aporte" : "Gasto"} registrado em "${name}"${overdraftNotice(result)}`);
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
    <AppShell current="overview" email={email} onLogout={onLogout}>
        {feedback && (
          <Notice tone="success" onClose={() => setFeedback(null)}>{feedback}</Notice>
        )}
        <PageHeader
          eyebrow={monthLabel}
          title="Visão geral"
          description="Veja o que já está reservado antes de decidir o próximo gasto."
          actions={
            <>
              <Button onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>Nova verba</Button>
              <Button variant="secondary" onClick={() => { clearFeedback(); setShowIncomeForm(true); }} aria-label="Configurar renda">Configurar renda</Button>
              <Button variant="secondary" onClick={() => { clearFeedback(); setShowGlossary(true); }}>Glossário de verbas</Button>
            </>
          }
        >
          <div className={styles.monthControl}>
            <label htmlFor="month-picker">Mês</label>
            <input id="month-picker" type="month" value={month} onChange={onMonthChange} />
          </div>
        </PageHeader>

        {loading && (
          <div role="status" aria-busy="true" className={styles.loadingState}>
            <div className={styles.skeleton} />
            <div className={styles.skeleton} />
          </div>
        )}
        {error && (
          <Notice tone="danger">
            {error}{" "}
            <Button variant="text" onClick={() => { void refreshEnvelopes(); void refreshSummary(); void refreshIncome(); }}>
              Tentar novamente
            </Button>
          </Notice>
        )}

        {!loading && (
          <div aria-busy={busy || undefined}>
            <IncomeCard income={income} month={month} loading={incomeLoading} error={incomeError} onEdit={() => { clearFeedback(); setShowIncomeForm(true); }} />
            {notConfigured && !incomeLoading && (
              <Notice tone="info">
                Nenhuma renda configurada para {monthLabel}. Clique em <strong>Configurar renda</strong> para definir a renda deste mês.
              </Notice>
            )}
            <BalanceSummary summary={summary} monthLabel={monthLabel} />
          </div>
        )}

        <div className={styles.contentGrid} aria-busy={busy || undefined}>
          <section id="verbas" className={styles.panel} aria-labelledby="verbas-titulo">
            <div className={styles.panelHeader}><div><h2 id="verbas-titulo">Suas verbas</h2><p>Valores disponíveis neste mês</p></div><Link href="/verbas">Ver todas →</Link></div>
            {loading ? (
              <div role="status" aria-busy="true" className={styles.loadingState}>
                <div className={styles.skeleton} />
                <div className={styles.skeleton} />
              </div>
            ) : envelopes.length === 0 && !error ? (
              <div className={styles.emptyState}>
                <p>Nenhuma verba neste mês — crie a primeira para começar a reservar.</p>
                <Button onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>Criar primeira verba</Button>
              </div>
            ) : (
              <ul className={styles.envelopeList}>
                {(summary?.envelopes ?? envelopes).slice(0, 4).map((envelope) => (
                  <EnvelopeCard
                    key={envelope.id}
                    envelope={envelope}
                    onRegisterExpense={(target) => { clearFeedback(); setExpenseTarget(target); }}
                    onArchive={(target) => { clearFeedback(); setPendingArchive(target); }}
                  />
                ))}
              </ul>
            )}
          </section>
          <section id="historico" className={styles.panel} aria-labelledby="historico-titulo">
            <div className={styles.panelHeader}>
              <div><h2 id="historico-titulo">Histórico de renda</h2><p>Alterações reais, mais recente primeiro</p></div>
              <Button variant="text" onClick={() => void refreshHistory()}>Atualizar</Button>
            </div>
            <IncomeHistory
              data={history}
              loading={historyLoading}
              error={historyError}
              onLoadMore={() => loadHistory((history?.page ?? 0) + 1)}
              onRetry={() => void refreshHistory()}
            />
            <div className={styles.panelFooter}>
              <Button variant="secondary" onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>Nova verba</Button>
              <p>
                {summary?.income ? "Registre gastos nas verbas ao lado. Saldo negativo gera alerta, não bloqueio." : "Configure sua renda para habilitar o cálculo de não alocado."}
              </p>
            </div>
          </section>
        </div>

        <Dialog open={showNewEnvelope} onClose={() => setShowNewEnvelope(false)} title="Nova verba">
          <EnvelopeForm
            onSuccess={handleEnvelopeSuccess}
            onCancel={() => setShowNewEnvelope(false)}
            onOpenGlossary={() => {
              setShowNewEnvelope(false);
              setShowGlossary(true);
            }}
          />
        </Dialog>

        <Dialog open={showIncomeForm} onClose={() => setShowIncomeForm(false)} title="Renda mensal">
          <IncomeForm initialAmount={income?.amount} onSuccess={handleIncomeSuccess} onCancel={() => setShowIncomeForm(false)} />
        </Dialog>

        <Dialog open={!!expenseTarget} onClose={() => setExpenseTarget(null)} title={expenseTarget ? `${expenseKind === "CONTRIBUTION" ? "Aportar" : "Registrar gasto"} em ${expenseTarget.name}` : "Registrar gasto"}>
          {expenseTarget && <ExpenseForm envelope={expenseTarget} kind={expenseKind} onSuccess={handleExpenseSuccess} onCancel={() => setExpenseTarget(null)} />}
        </Dialog>

        <Dialog open={showGlossary} onClose={() => setShowGlossary(false)} title="Glossário de verbas">
          <Glossary />
        </Dialog>

        <ConfirmDialog
          open={!!pendingArchive}
          title={pendingArchive ? `Encerrar ${pendingArchive.name}?` : "Encerrar verba?"}
          message={ARCHIVE_MESSAGE}
          confirmLabel="Encerrar"
          onConfirm={() => void confirmArchive()}
          onCancel={() => setPendingArchive(null)}
        />
    </AppShell>
  );
}
