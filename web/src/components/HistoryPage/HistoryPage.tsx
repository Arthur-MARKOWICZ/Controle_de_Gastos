"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { Dialog } from "../ui/Dialog";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Notice } from "../ui/Notice";
import { Field } from "../ui/Field";
import { Button } from "../ui/Button";
import {
  createApiClient,
  type EnvelopeDTO,
  type HistoryItemDTO,
  type HistoryPageDTO,
  type HistorySummaryDTO,
  type LedgerEntryRevisionDTO,
} from "../../lib/api";
import { currentMonthSaoPaulo, formatDateLabel, todaySaoPaulo } from "../../lib/dates";
import { formatBRL, formatBRLInputMask, maskToPlain } from "../../lib/money";
import styles from "./HistoryPage.module.css";
import { AppShell } from "../AppShell/AppShell";
import { PageHeader } from "../ui/PageHeader";
import { ExpenseForm, overdraftNotice, type ExpenseResult } from "../ExpenseForm/ExpenseForm";
import { isContributionPurpose } from "../EnvelopeCard/EnvelopeCard";

type Props = { email: string; onLogout(): void };
const purposes = {
  LIMIT: "Limite",
  GOAL: "Meta de aporte",
  FIXED: "Compromisso fixo",
  SAVINGS_TARGET: "Meta de acumulação",
  ANNUAL_EXPENSE: "Gasto anual",
} as const;

function defaultFrom() {
  return `${currentMonthSaoPaulo()}-01`;
}

export function HistoryPage({ email, onLogout }: Props) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { client } = useAuth();
  const from = searchParams.get("from") ?? defaultFrom();
  const to = searchParams.get("to") ?? todaySaoPaulo();
  const page = Number(searchParams.get("page") ?? "0") || 0;
  const includeDeleted = searchParams.get("includeDeleted") === "true";
  const [data, setData] = useState<HistoryPageDTO | null>(null);
  const [summary, setSummary] = useState<HistorySummaryDTO | null>(null);
  const [envelopes, setEnvelopes] = useState<EnvelopeDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<HistoryItemDTO | null>(null);
  const [amount, setAmount] = useState("");
  const [envelopeId, setEnvelopeId] = useState("");
  const [description, setDescription] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<HistoryItemDTO | null>(null);
  const [revisionsTarget, setRevisionsTarget] = useState<HistoryItemDTO | null>(null);
  const [revisions, setRevisions] = useState<LedgerEntryRevisionDTO[]>([]);
  const [revisionsError, setRevisionsError] = useState<string | null>(null);
  const [revisionsLoading, setRevisionsLoading] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [showEnvelopePicker, setShowEnvelopePicker] = useState(false);
  const [expenseTarget, setExpenseTarget] = useState<EnvelopeDTO | null>(null);
  const hasDataRef = useRef(false);

  const load = useCallback(async () => {
    if (from > to) {
      setError("A data inicial deve ser anterior à final.");
      setLoading(false);
      return;
    }
    if (hasDataRef.current) {
      setBusy(true);
    } else {
      setData(null);
      setSummary(null);
      setLoading(true);
    }
    setError(null);
    try {
      const api = createApiClient(client);
      const [history, report, visibleEnvelopes] = await Promise.all([
        api.getHistory(from, to, page, 10, includeDeleted),
        api.getHistorySummary(from, to),
        api.getEnvelopes(to.slice(0, 7)),
      ]);
      setData(history);
      setSummary(report);
      setEnvelopes(visibleEnvelopes);
      hasDataRef.current = true;
    } catch {
      setError("Não foi possível carregar o histórico.");
    } finally {
      setLoading(false);
      setBusy(false);
    }
  }, [client, from, to, page, includeDeleted]);

  useEffect(() => {
    hasDataRef.current = false;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const grouped = useMemo(() => {
    const groups: Record<keyof typeof purposes, HistoryItemDTO[]> = {
      LIMIT: [],
      GOAL: [],
      FIXED: [],
      SAVINGS_TARGET: [],
      ANNUAL_EXPENSE: [],
    };
    for (const item of data?.items ?? []) groups[item.purpose].push(item);
    return groups;
  }, [data]);

  function navigate(next: Record<string, string | number | boolean | undefined>) {
    const params = new URLSearchParams();
    const merged = { from, to, page, includeDeleted, ...next };
    Object.entries(merged).forEach(([key, value]) => {
      if (value !== false && value !== undefined) params.set(key, String(value));
    });
    router.push(`/gastos?${params}`);
  }

  function startEdit(item: HistoryItemDTO) {
    setFeedback(null);
    setFormError(null);
    setEditing(item);
    setEnvelopeId(item.entry.envelopeId);
    setAmount(formatBRLInputMask(item.entry.amount.amount));
    setDescription(item.entry.description ?? "");
  }

  async function submitEdit(event: FormEvent) {
    event.preventDefault();
    const plain = maskToPlain(amount);
    if (!editing || !envelopeId) return;
    if (!/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") {
      setFormError("Informe um valor maior que zero com duas casas decimais.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await createApiClient(client).updateHistoryEntry(editing.entry.id, {
        envelopeId,
        amount: { amount: plain, currency: "BRL" },
        description: description.trim() || null,
      });
      setEditing(null);
      setFeedback("Gasto atualizado.");
      void load();
    } catch {
      setFormError("Não foi possível atualizar o gasto.");
    } finally {
      setSaving(false);
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) return;
    const item = pendingDelete;
    setPendingDelete(null);
    try {
      await createApiClient(client).deleteHistoryEntry(item.entry.id);
      setFeedback("Gasto excluído.");
      void load();
    } catch {
      setFeedback("Não foi possível excluir o gasto.");
    }
  }

  /** Verbas em que esta sessão pode lançar: dono ou participante. */
  const spendableEnvelopes = envelopes.filter((envelope) => !envelope.archivedAt);
  const canRegister = spendableEnvelopes.length > 0;
  const expenseKind = expenseTarget
    && expenseTarget.role === "OWNER"
    && isContributionPurpose(expenseTarget.purpose)
    ? "CONTRIBUTION"
    : "EXPENSE";

  /** Primeiro a verba, depois o formulário: o lançamento sempre nomeia onde cai. */
  function startRegister() {
    setFeedback(null);
    setShowEnvelopePicker(true);
  }

  function handleExpenseSuccess(result: ExpenseResult) {
    const name = expenseTarget?.name ?? "verba";
    setExpenseTarget(null);
    setFeedback(`${expenseKind === "CONTRIBUTION" ? "Aporte" : "Gasto"} registrado em "${name}"${overdraftNotice(result)}`);
    void load();
  }

  const card = (label: string, value?: string) => (
    <div className={styles.metric}>
      <dt>{label}</dt>
      <dd>{value ? formatBRL(value) : "—"}</dd>
    </div>
  );
  const chartMax = Math.max(...(summary?.monthlyTotals.map((total) => Number(total.amount.amount)) ?? [0]), 1);
  const purposeMax = Math.max(...(summary?.purposeTotals.map((total) => Number(total.amount.amount)) ?? [0]), 1);
  const ownerEnvelopes = envelopes.filter((envelope) => envelope.role === "OWNER");

  return (
    <AppShell current="expenses" email={email} onLogout={onLogout}>
      <div className={styles.page}>
        <PageHeader
          eyebrow="Registro financeiro"
          title="Histórico"
          description={
            canRegister
              ? "Consulte, corrija e registre gastos das verbas que você pode visualizar."
              : "Consulte os gastos das verbas que você pode visualizar."
          }
          actions={canRegister ? <Button onClick={startRegister}>Registrar gasto</Button> : undefined}
        />
        <form
          className={styles.filters}
          onSubmit={(event) => {
            event.preventDefault();
            navigate({ page: 0 });
          }}
        >
          <label>
            De
            <input type="date" value={from} onChange={(event) => navigate({ from: event.target.value, page: 0 })} />
          </label>
          <label>
            Até
            <input type="date" value={to} onChange={(event) => navigate({ to: event.target.value, page: 0 })} />
          </label>
          <label className={styles.checkbox}>
            <input
              type="checkbox"
              checked={includeDeleted}
              onChange={(event) => navigate({ includeDeleted: event.target.checked, page: 0 })}
            />{" "}
            Incluir excluídos
          </label>
        </form>
        {feedback && (
          <Notice tone="success" onClose={() => setFeedback(null)}>
            {feedback}
          </Notice>
        )}
        {loading && (
          <div role="status" aria-busy="true" className={styles.loadingState}>
            <div className={styles.skeleton} />
            <div className={styles.skeleton} />
          </div>
        )}
        {error && (
          <p role="alert" className={styles.error}>
            {error}{" "}
            <button type="button" onClick={() => void load()}>
              Tentar novamente
            </button>
          </p>
        )}
        {!loading && !error && summary && (
          <div aria-busy={busy || undefined}>
            <dl className={styles.metrics}>
              {card("Renda do período", summary.income.amount)}
              {card("Consumo do período", summary.expenses.amount)}
              {card("Aplicado no período", summary.applied.amount)}
              {card("Saldo líquido", summary.netBalance.amount)}
              {card("Saldo acumulado", summary.accumulatedBalance.amount)}
            </dl>
            <section className={styles.charts} aria-label="Gráficos de gastos">
              <div className={styles.chart}>
                <h2>Gastos por mês</h2>
                {summary.monthlyTotals.length === 0 ? (
                  <p>Sem gastos no período.</p>
                ) : (
                  <ul>
                    {summary.monthlyTotals.map((total) => (
                      <li key={total.month}>
                        <span>{total.month}</span>
                        <i style={{ width: `${(Number(total.amount.amount) / chartMax) * 100}%` }} />
                        <strong>{formatBRL(total.amount.amount)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <section className={styles.chart} aria-label="Gastos por tipo">
                <h2>Gastos por tipo</h2>
                {summary.purposeTotals.length === 0 ? (
                  <p>Sem gastos no período.</p>
                ) : (
                  <ul>
                    {summary.purposeTotals.map((total) => (
                      <li key={total.purpose}>
                        <span>
                          <span className={styles.dot} data-purpose={total.purpose} />
                          {purposes[total.purpose]}
                        </span>
                        <i
                          data-purpose={total.purpose}
                          style={{ width: `${(Number(total.amount.amount) / purposeMax) * 100}%` }}
                        />
                        <strong>{formatBRL(total.amount.amount)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </section>
            <section className={styles.entries} aria-labelledby="gastos-titulo">
              <header>
                <h2 id="gastos-titulo">Gastos</h2>
                <span>Página {(data?.page ?? 0) + 1}</span>
              </header>
              {(data?.items.length ?? 0) === 0 ? (
                <div className={styles.empty}>
                  <p>Nenhum gasto no período selecionado.</p>
                  {canRegister && <Button onClick={startRegister}>Registrar gasto</Button>}
                </div>
              ) : (
                (Object.keys(purposes) as (keyof typeof purposes)[]).map(
                  (purpose) =>
                    grouped[purpose].length > 0 && (
                      <div className={styles.group} key={purpose}>
                        <h3>{purposes[purpose]}</h3>
                        <ul>
                          {grouped[purpose].map((item) => (
                            <li key={item.entry.id} data-deleted={item.entry.deletedAt ? "true" : "false"}>
                              <div>
                                <strong>{item.envelopeName}</strong>
                                <span>
                                  {formatDateLabel(item.entry.occurredAt)}
                                  {item.entry.description ? ` · ${item.entry.description}` : ""}
                                </span>
                              </div>
                              <strong>{formatBRL(item.entry.amount.amount)}</strong>
                              {item.role === "OWNER" && (
                                <span className={styles.actions}>
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setFeedback(null);
                                      setRevisionsTarget(item);
                                      setRevisions([]);
                                      setRevisionsError(null);
                                      setRevisionsLoading(true);
                                      void createApiClient(client)
                                        .listEntryRevisions(item.entry.id)
                                        .then((page) => setRevisions(page.items))
                                        .catch(() => setRevisionsError("Não foi possível carregar as alterações."))
                                        .finally(() => setRevisionsLoading(false));
                                    }}
                                  >
                                    Alterações
                                  </button>
                                  {!item.entry.deletedAt && (
                                    <>
                                      <button type="button" onClick={() => startEdit(item)}>
                                        Editar
                                      </button>
                                      <button
                                        type="button"
                                        onClick={() => {
                                          setFeedback(null);
                                          setPendingDelete(item);
                                        }}
                                      >
                                        Excluir
                                      </button>
                                    </>
                                  )}
                                </span>
                              )}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ),
                )
              )}
            </section>
            <nav className={styles.pagination} aria-label="Paginação do histórico">
              <button type="button" disabled={page === 0} onClick={() => navigate({ page: page - 1 })}>
                Anterior
              </button>
              <button type="button" disabled={!data?.hasNext} onClick={() => navigate({ page: page + 1 })}>
                Próxima
              </button>
            </nav>
          </div>
        )}
      </div>
      <Dialog open={showEnvelopePicker} onClose={() => setShowEnvelopePicker(false)} title="Registrar gasto">
        <p>Em qual verba?</p>
        <ul className={styles.envelopePicker}>
          {spendableEnvelopes.map((envelope) => (
            <li key={envelope.id}>
              <Button
                variant="secondary"
                onClick={() => {
                  setShowEnvelopePicker(false);
                  setExpenseTarget(envelope);
                }}
              >
                {envelope.name}
              </Button>
            </li>
          ))}
        </ul>
      </Dialog>

      <Dialog
        open={!!expenseTarget}
        onClose={() => setExpenseTarget(null)}
        title={expenseTarget ? `${expenseKind === "CONTRIBUTION" ? "Aportar" : "Registrar gasto"} em ${expenseTarget.name}` : "Registrar gasto"}
      >
        {expenseTarget && (
          <ExpenseForm
            envelope={expenseTarget}
            kind={expenseKind}
            onSuccess={handleExpenseSuccess}
            onCancel={() => setExpenseTarget(null)}
          />
        )}
      </Dialog>

      <Dialog open={!!editing} onClose={() => setEditing(null)} title="Editar gasto">
        <form className={styles.editForm} onSubmit={submitEdit}>
          <Field id="history-amount" label="Valor (BRL)" error={formError ?? undefined}>
            <input
              id="history-amount"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(formatBRLInputMask(event.target.value))}
              required
            />
          </Field>
          <Field id="history-envelope" label="Verba">
            <select id="history-envelope" value={envelopeId} onChange={(event) => setEnvelopeId(event.target.value)}>
              {ownerEnvelopes.map((envelope) => (
                <option key={envelope.id} value={envelope.id}>
                  {envelope.name}
                </option>
              ))}
            </select>
          </Field>
          <Field id="history-description" label="Descrição">
            <input
              id="history-description"
              value={description}
              maxLength={140}
              onChange={(event) => setDescription(event.target.value)}
            />
          </Field>
          <p>A data do gasto não pode ser alterada.</p>
          <div>
            <Button variant="secondary" type="button" onClick={() => setEditing(null)} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" busy={saving}>
              Salvar
            </Button>
          </div>
        </form>
      </Dialog>
      <Dialog
        open={!!revisionsTarget}
        onClose={() => setRevisionsTarget(null)}
        title={revisionsTarget ? `Alterações · ${revisionsTarget.envelopeName}` : "Alterações"}
      >
        {revisionsLoading && <p role="status">Carregando alterações…</p>}
        {revisionsError && <Notice tone="danger">{revisionsError}</Notice>}
        {!revisionsLoading && !revisionsError && revisions.length === 0 && (
          <p role="status">Nenhuma edição ou exclusão registrada ainda.</p>
        )}
        {!revisionsLoading && revisions.length > 0 && (
          <ul className={styles.group}>
            {revisions.map((revision) => (
              <li key={revision.id}>
                <div>
                  <strong>{revision.action === "DELETE" ? "Exclusão" : "Edição"}</strong>
                  <span>
                    {new Date(revision.changedAt).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })}
                    {" · "}
                    {formatBRL(revision.previousAmount.amount)}
                    {revision.previousDescription ? ` · ${revision.previousDescription}` : ""}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
        <Button variant="secondary" type="button" onClick={() => setRevisionsTarget(null)}>
          Fechar
        </Button>
      </Dialog>
      <ConfirmDialog
        open={!!pendingDelete}
        title="Excluir lançamento?"
        message={
          pendingDelete
            ? `O valor volta para o saldo de ${pendingDelete.envelopeName}. O registro continua visível ao marcar "mostrar lançamentos excluídos".`
            : "O valor volta para o saldo da verba."
        }
        confirmLabel="Excluir"
        onConfirm={() => void confirmDelete()}
        onCancel={() => setPendingDelete(null)}
      />
    </AppShell>
  );
}
