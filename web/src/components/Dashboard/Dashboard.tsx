"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";
import styles from "./Dashboard.module.css";
import { useEnvelopes } from "../../hooks/useEnvelopes";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import { useLimitSpendingPace } from "../../hooks/useLimitSpendingPace";
import { useIncome, useIncomeHistory } from "../../hooks/useIncome";
import { currentMonthSaoPaulo, formatDateLabel, formatMonthLabel, parseMonthParam } from "../../lib/dates";
import { formatBRL } from "../../lib/money";
import { spendingPaceLabel } from "../../lib/spendingPace";
import { MonthSummary } from "../MonthSummary/MonthSummary";
import { IncomeForm } from "../IncomeForm/IncomeForm";
import { IncomeHistory } from "../IncomeHistory/IncomeHistory";
import { EnvelopeCard, isContributionPurpose } from "../EnvelopeCard/EnvelopeCard";
import { Dialog } from "../ui/Dialog";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Button } from "../ui/Button";
import { Notice } from "../ui/Notice";
import { PageHeader } from "../ui/PageHeader";
import { useToast, type ToastAction } from "../ui/ToastProvider";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm, overdraftNotice, type ExpenseResult } from "../ExpenseForm/ExpenseForm";
import { QuickExpenseDialog } from "../QuickExpenseDialog/QuickExpenseDialog";
import { Glossary } from "../Glossary/Glossary";
import type { EnvelopeDTO, HistoryItemDTO } from "../../lib/api";
import { createApiClient } from "../../lib/api";
import { useAuth } from "../../auth/auth-context";
import { AppShell } from "../AppShell/AppShell";
import { ParticipantsPanel } from "../ParticipantsPanel/ParticipantsPanel";
import { PendingOccurrences } from "../PendingOccurrences/PendingOccurrences";
import { RecurringEntriesPanel } from "../RecurringEntriesPanel/RecurringEntriesPanel";
import { PersistentAlerts } from "../PersistentAlerts/PersistentAlerts";

const ARCHIVE_MESSAGE =
  "A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no histórico.";

function monthRange(month: string): { from: string; to: string } {
  const [y, m] = month.split("-").map(Number);
  const lastDay = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return {
    from: `${month}-01`,
    to: `${month}-${String(lastDay).padStart(2, "0")}`,
  };
}

function prioritizeEnvelopes(envelopes: EnvelopeDTO[]): EnvelopeDTO[] {
  return [...envelopes].sort((a, b) => {
    if (a.isNegative !== b.isNegative) return a.isNegative ? -1 : 1;
    const baseA = Number(a.baseAmount.amount);
    const baseB = Number(b.baseAmount.amount);
    const ratioA = baseA > 0 ? Number(a.available.amount) / baseA : 1;
    const ratioB = baseB > 0 ? Number(b.available.amount) / baseB : 1;
    if (ratioA !== ratioB) return ratioA - ratioB;
    return a.name.localeCompare(b.name, "pt-BR");
  });
}

export function Dashboard({ email, onLogout }: { email: string; onLogout(): void }) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { client } = useAuth();
  const { showToast } = useToast();
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
  const { byEnvelopeId: paceById, refresh: refreshPace } = useLimitSpendingPace(month);
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
  const [showQuickExpense, setShowQuickExpense] = useState(false);
  const [expenseTarget, setExpenseTarget] = useState<EnvelopeDTO | null>(null);
  const [participantsTarget, setParticipantsTarget] = useState<EnvelopeDTO | null>(null);
  const [recurringTarget, setRecurringTarget] = useState<EnvelopeDTO | null>(null);
  const [showGlossary, setShowGlossary] = useState(false);
  const [pendingArchive, setPendingArchive] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [alertsRefreshKey, setAlertsRefreshKey] = useState(0);
  const [recentEntries, setRecentEntries] = useState<HistoryItemDTO[]>([]);
  const [recentLoading, setRecentLoading] = useState(false);
  const api = useMemo(() => createApiClient(client), [client]);

  const loadRecent = useCallback(() => {
    const { from, to } = monthRange(month);
    setRecentLoading(true);
    void Promise.resolve()
      .then(() => api.getHistory(from, to, 0, 5, false))
      .then((page) => setRecentEntries(page?.items ?? []))
      .catch(() => setRecentEntries([]))
      .finally(() => setRecentLoading(false));
  }, [api, month]);

  useEffect(() => {
    const timer = window.setTimeout(() => loadRecent(), 0);
    return () => window.clearTimeout(timer);
  }, [loadRecent, alertsRefreshKey]);

  const loading = envelopesLoading || summaryLoading || incomeLoading;
  const busy = envelopesBusy || summaryBusy || incomeBusy || historyBusy;
  const error = envelopesError || summaryError;

  function notifySuccess(message: string, action?: ToastAction) {
    setFeedback(message);
    showToast(action ? { message, action } : message);
  }

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
      notifySuccess(`Verba "${envelope.name}" encerrada`);
      void refreshEnvelopes();
      void refreshSummary();
      void refreshPace();
    } catch {
      setFeedback("Não foi possível encerrar a verba");
    }
  }

  function handleEnvelopeSuccess() {
    setShowNewEnvelope(false);
    notifySuccess("Verba criada com sucesso");
    void refreshEnvelopes();
    void refreshSummary();
    void refreshPace();
  }

  const balanceEnvelopes = useMemo(() => summary?.envelopes ?? [], [summary]);
  const priorityEnvelopes = useMemo(() => prioritizeEnvelopes(balanceEnvelopes), [balanceEnvelopes]);

  const expenseKind = expenseTarget
    && expenseTarget.role === "OWNER"
    && isContributionPurpose(expenseTarget.purpose)
    ? "CONTRIBUTION"
    : "EXPENSE";

  function handleExpenseSuccess(result: ExpenseResult) {
    const name = expenseTarget?.name ?? "verba";
    setExpenseTarget(null);
    notifySuccess(
      `${expenseKind === "CONTRIBUTION" ? "Aporte" : "Gasto"} registrado em "${name}"${overdraftNotice(result)}`,
      {
        label: "Desfazer",
        onClick: () => {
          void createApiClient(client)
            .deleteHistoryEntry(result.entryId)
            .then(() => {
              setFeedback("Lançamento desfeito.");
              setAlertsRefreshKey((key) => key + 1);
              void refreshEnvelopes();
              void refreshSummary();
            })
            .catch(() => {
              setFeedback("Não foi possível desfazer o lançamento.");
            });
        },
      },
    );
    setAlertsRefreshKey((key) => key + 1);
    void refreshEnvelopes();
    void refreshSummary();
    void refreshPace();
  }

  function handleIncomeSuccess() {
    setShowIncomeForm(false);
    notifySuccess("Renda salva com sucesso");
    void refreshIncome();
    void refreshSummary();
    void refreshHistory();
  }

  return (
    <AppShell
      current="overview"
      email={email}
      month={month}
      onLogout={onLogout}
      onQuickExpense={() => { clearFeedback(); setShowQuickExpense(true); }}
    >
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
          actions={
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              {balanceEnvelopes.length > 0 && (
                <Button onClick={() => { clearFeedback(); setShowQuickExpense(true); }}>
                  Registrar gasto
                </Button>
              )}
              <Button variant={balanceEnvelopes.length > 0 ? "secondary" : "primary"} onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>
                Nova verba
              </Button>
            </div>
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
            {Object.keys(paceById).length > 0 && (
              <section className={styles.panel} aria-label="Ritmo das verbas de limite">
                <div className={styles.panelHeader}>
                  <div>
                    <h2>Ritmo das verbas de limite</h2>
                    <p>Projeção com base no gasto até hoje neste mês</p>
                  </div>
                </div>
                <ul className={styles.envelopeList}>
                  {Object.values(paceById).map((pace) => (
                    <li key={pace.envelopeId}>
                      <strong>{pace.name}</strong>
                      {" — "}
                      {spendingPaceLabel(pace)}
                    </li>
                  ))}
                </ul>
              </section>
            )}
            {notConfigured && !incomeLoading && (
              <Notice tone="info">
                Nenhuma renda configurada para {monthLabel}. Use <strong>Configurar renda</strong> no resumo para definir a renda deste mês.
              </Notice>
            )}
          </div>
        )}

        <div className={styles.contentGrid} aria-busy={busy || undefined}>
          <section id="verbas" className={styles.panel} aria-label="Suas verbas" aria-labelledby="verbas-titulo">
            <div className={styles.panelHeader}>
              <div><h2 id="verbas-titulo">Verbas prioritárias</h2><p>Negativas e mais próximas do limite primeiro</p></div>
              <Link href={`/verbas?month=${month}`}>Ver todas →</Link>
            </div>
            {loading ? (
              <div role="status" aria-busy="true" className={styles.loadingState}>
                <div className={styles.skeleton} />
                <div className={styles.skeleton} />
              </div>
            ) : !summary ? null : priorityEnvelopes.length === 0 ? (
              <div className={styles.emptyState}>
                <p>Nenhuma verba neste mês — crie a primeira para começar a reservar.</p>
                <Button onClick={() => { clearFeedback(); setShowNewEnvelope(true); }}>Criar primeira verba</Button>
              </div>
            ) : (
              <ul className={styles.envelopeList}>
                {priorityEnvelopes.slice(0, 5).map((envelope) => (
                  <EnvelopeCard
                    key={envelope.id}
                    envelope={envelope}
                    pace={envelope.purpose === "LIMIT" ? paceById[envelope.id] ?? null : null}
                    onRegisterExpense={(target) => { clearFeedback(); setExpenseTarget(target); }}
                    onManageParticipants={(target) => { clearFeedback(); setParticipantsTarget(target); }}
                    onManageRecurring={(target) => { clearFeedback(); setRecurringTarget(target); }}
                    onArchive={(target) => { clearFeedback(); setPendingArchive(target); }}
                  />
                ))}
              </ul>
            )}
          </section>

          <section id="ultimos-lancamentos" className={styles.panel} aria-labelledby="ultimos-titulo">
            <div className={styles.panelHeader}>
              <div><h2 id="ultimos-titulo">Últimos lançamentos</h2><p>Gastos recentes de {monthLabel}</p></div>
              <Link href="/gastos">Ver histórico →</Link>
            </div>
            {recentLoading && recentEntries.length === 0 ? (
              <p className={styles.listState} role="status">Carregando lançamentos…</p>
            ) : recentEntries.length === 0 ? (
              <div className={styles.emptyState}>
                <p>Nenhum gasto registrado neste mês ainda.</p>
                {balanceEnvelopes.length > 0 && (
                  <Button variant="secondary" onClick={() => { clearFeedback(); setShowQuickExpense(true); }}>
                    Registrar primeiro gasto
                  </Button>
                )}
              </div>
            ) : (
              <ul className={styles.activityList}>
                {recentEntries.map((item) => (
                  <li key={item.entry.id}>
                    <span className={styles.activityMark} aria-hidden="true" />
                    <div>
                      <strong>{item.envelopeName}</strong>
                      <span>
                        {formatDateLabel(item.entry.occurredAt)}
                        {item.entry.description ? ` · ${item.entry.description}` : ""}
                      </span>
                    </div>
                    <span className={styles.activityAmount}>{formatBRL(item.entry.amount.amount)}</span>
                  </li>
                ))}
              </ul>
            )}
            <div className={styles.panelFooter}>
              <p>
                {summary?.income
                  ? "Atalho: pressione N ou Ctrl+N para lançar um gasto de qualquer tela."
                  : "Configure sua renda para habilitar o cálculo de não alocado."}
              </p>
            </div>
          </section>
        </div>

        <QuickExpenseDialog
          open={showQuickExpense}
          envelopes={balanceEnvelopes}
          month={month}
          onClose={() => setShowQuickExpense(false)}
          onSuccess={(result) => {
            setShowQuickExpense(false);
            notifySuccess(
              `${result.kind === "CONTRIBUTION" ? "Aporte" : "Gasto"} registrado em "${result.envelopeName}"${overdraftNotice(result)}`,
              {
                label: "Desfazer",
                onClick: () => {
                  void createApiClient(client)
                    .deleteHistoryEntry(result.entryId)
                    .then(() => {
                      setFeedback("Lançamento desfeito.");
                      setAlertsRefreshKey((key) => key + 1);
                      void refreshEnvelopes();
                      void refreshSummary();
                    })
                    .catch(() => {
                      setFeedback("Não foi possível desfazer o lançamento.");
                    });
                },
              },
            );
            setAlertsRefreshKey((key) => key + 1);
            void refreshEnvelopes();
            void refreshSummary();
          }}
        />

        <Dialog open={showNewEnvelope} onClose={() => setShowNewEnvelope(false)} title="Nova verba">
          <EnvelopeForm
            unallocatedAmount={summary?.unallocated.amount}
            onSuccess={handleEnvelopeSuccess}
            onCancel={() => setShowNewEnvelope(false)}
          />
        </Dialog>

        <Dialog open={showIncomeForm} onClose={() => setShowIncomeForm(false)} title="Renda mensal">
          <IncomeForm initialAmount={income?.amount} onSuccess={handleIncomeSuccess} onCancel={() => setShowIncomeForm(false)} />
          <div style={{ marginTop: "1.5rem", paddingTop: "1rem", borderTop: "1px solid var(--border)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.75rem" }}>
              <h3 style={{ fontSize: "0.9375rem" }}>Histórico de renda</h3>
              <Button variant="text" onClick={() => void refreshHistory()}>Atualizar</Button>
            </div>
            <IncomeHistory
              data={history}
              loading={historyLoading}
              error={historyError}
              onLoadMore={() => loadHistory((history?.page ?? 0) + 1)}
              onRetry={() => void refreshHistory()}
            />
          </div>
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
