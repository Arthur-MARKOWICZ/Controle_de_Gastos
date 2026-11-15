"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";
import styles from "./Dashboard.module.css";
import { useEnvelopes } from "../../hooks/useEnvelopes";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import { useIncome, useIncomeHistory } from "../../hooks/useIncome";
import { currentMonthSaoPaulo, formatMonthLabel, parseMonthParam } from "../../lib/dates";
import { MonthSummary } from "../MonthSummary/MonthSummary";
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
import { ParticipantsPanel } from "../ParticipantsPanel/ParticipantsPanel";
import { PendingOccurrences } from "../PendingOccurrences/PendingOccurrences";
import { RecurringEntriesPanel } from "../RecurringEntriesPanel/RecurringEntriesPanel";
import { PersistentAlerts } from "../PersistentAlerts/PersistentAlerts";

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
  const [participantsTarget, setParticipantsTarget] = useState<EnvelopeDTO | null>(null);
  const [recurringTarget, setRecurringTarget] = useState<EnvelopeDTO | null>(null);
  const [showGlossary, setShowGlossary] = useState(false);
  const [pendingArchive, setPendingArchive] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [alertsRefreshKey, setAlertsRefreshKey] = useState(0);
  const api = useMemo(() => createApiClient(client), [client]);

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

  const balanceEnvelopes = summary?.envelopes ?? [];
  const expenseKind = expenseTarget
    && expenseTarget.role === "OWNER"
    && isContributionPurpose(expenseTarget.purpose)
    ? "CONTRIBUTION"
    : "EXPENSE";

  function handleExpenseSuccess(result: ExpenseResult) {
    const name = expenseTarget?.name ?? "verba";
    setExpenseTarget(null);
    setFeedback(`${expenseKind === "CONTRIBUTION" ? "Aporte" : "Gasto"} registrado em "${name}"${overdraftNotice(result)}`);
    setAlertsRefreshKey((key) => key + 1);
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
          description={
            <>
              Veja o que já está reservado antes de decidir o próximo gasto.{" "}
              <Button variant="text" onClick={() => { clearFeedback(); setShowGlossary(true); }}>
                Ver glossário de verbas
              </Button>
            </>
          }
          actions={<Button onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>Nova verba</Button>}
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
            <PersistentAlerts client={api} refreshKey={alertsRefreshKey} />
            <PendingOccurrences
              client={api}
              refreshKey={alertsRefreshKey}
              onChanged={() => {
                setAlertsRefreshKey((key) => key + 1);
                void refreshEnvelopes();
                void refreshSummary();
              }}
            />
            <MonthSummary
              summary={summary}
              income={income}
              month={month}
              monthLabel={monthLabel}
              loading={incomeLoading}
              error={incomeError}
              onConfigureIncome={() => { clearFeedback(); setShowIncomeForm(true); }}
            />
            {notConfigured && !incomeLoading && (
              <Notice tone="info">
                Nenhuma renda configurada para {monthLabel}. Use <strong>Configurar renda</strong> no resumo para definir a renda deste mês.
              </Notice>
            )}
          </div>
        )}

        <div className={styles.contentGrid} aria-busy={busy || undefined}>
          <section id="verbas" className={styles.panel} aria-label="Suas verbas" aria-labelledby="verbas-titulo">
            <div className={styles.panelHeader}><div><h2 id="verbas-titulo">Suas verbas</h2><p>Valores disponíveis neste mês</p></div><Link href="/verbas">Ver todas →</Link></div>
            {loading ? (
              <div role="status" aria-busy="true" className={styles.loadingState}>
                <div className={styles.skeleton} />
                <div className={styles.skeleton} />
              </div>
            ) : !summary ? null : balanceEnvelopes.length === 0 ? (
              <div className={styles.emptyState}>
                <p>Nenhuma verba neste mês — crie a primeira para começar a reservar.</p>
                <Button onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>Criar primeira verba</Button>
              </div>
            ) : (
              <ul className={styles.envelopeList}>
                {balanceEnvelopes.slice(0, 4).map((envelope) => (
                  <EnvelopeCard
                    key={envelope.id}
                    envelope={envelope}
                    onRegisterExpense={(target) => { clearFeedback(); setExpenseTarget(target); }}
                    onManageParticipants={(target) => { clearFeedback(); setParticipantsTarget(target); }}
                    onManageRecurring={(target) => { clearFeedback(); setRecurringTarget(target); }}
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

        <Dialog
          open={!!recurringTarget}
          onClose={() => setRecurringTarget(null)}
          title={recurringTarget ? `Recorrências de ${recurringTarget.name}` : "Recorrências"}
        >
          {recurringTarget && (
            <RecurringEntriesPanel client={api} envelope={recurringTarget} onClose={() => setRecurringTarget(null)} />
          )}
        </Dialog>
        <Dialog
          open={!!participantsTarget}
          onClose={() => setParticipantsTarget(null)}
          title={participantsTarget ? `Participantes de ${participantsTarget.name}` : "Participantes"}
        >
          {participantsTarget && (
            <ParticipantsPanel
              client={api}
              envelopeId={participantsTarget.id}
              envelopeName={participantsTarget.name}
              onClose={() => setParticipantsTarget(null)}
            />
          )}
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
