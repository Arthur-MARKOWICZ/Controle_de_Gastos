"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { Dialog } from "../ui/Dialog";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Notice } from "../ui/Notice";
import { Field } from "../ui/Field";
import { Button } from "../ui/Button";
import { useToast } from "../ui/ToastProvider";
import {
  createApiClient,
  type EnvelopeDTO,
  type HistoryAuthorFilter,
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
import { overdraftNotice } from "../ExpenseForm/ExpenseForm";
import { QuickExpenseDialog } from "../QuickExpenseDialog/QuickExpenseDialog";

type Props = { email: string; onLogout(): void };
const purposes = {
  LIMIT: "Limite",
  GOAL: "Meta de aporte",
  FIXED: "Compromisso fixo",
  SAVINGS_TARGET: "Meta de acumulação",
  ANNUAL_EXPENSE: "Gasto anual",
} as const;

const SEARCH_DEBOUNCE_MS = 400;

function parseAuthor(value: string | null): HistoryAuthorFilter | undefined {
  return value === "ME" || value === "OTHERS" ? value : undefined;
}

function defaultFrom() {
  return `${currentMonthSaoPaulo()}-01`;
}

function shiftDays(isoDate: string, deltaDays: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + deltaDays));
  return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, "0")}-${String(dt.getUTCDate()).padStart(2, "0")}`;
}

function previousMonthRange(): { from: string; to: string } {
  const [y, m] = currentMonthSaoPaulo().split("-").map(Number);
  const start = new Date(Date.UTC(y, m - 2, 1));
  const year = start.getUTCFullYear();
  const month = start.getUTCMonth() + 1;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const mm = String(month).padStart(2, "0");
  return { from: `${year}-${mm}-01`, to: `${year}-${mm}-${String(lastDay).padStart(2, "0")}` };
}

function formatDayHeading(isoDate: string, today: string): string {
  if (isoDate === today) return `Hoje · ${formatDateLabel(isoDate)}`;
  if (isoDate === shiftDays(today, -1)) return `Ontem · ${formatDateLabel(isoDate)}`;
  return formatDateLabel(isoDate);
}

export function HistoryPage({ email, onLogout }: Props) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { client } = useAuth();
  const { showToast } = useToast();
  const today = todaySaoPaulo();
  const from = searchParams.get("from") ?? defaultFrom();
  const to = searchParams.get("to") ?? today;
  const page = Number(searchParams.get("page") ?? "0") || 0;
  const includeDeleted = searchParams.get("includeDeleted") === "true";
  const q = searchParams.get("q")?.trim() ?? "";
  const envelopeFilter = searchParams.get("envelopeId") ?? "";
  const author = parseAuthor(searchParams.get("author"));
  const filtersActive = q !== "" || envelopeFilter !== "" || author !== undefined;
  const activeExtraFilterCount =
    (envelopeFilter !== "" ? 1 : 0) + (author !== undefined ? 1 : 0) + (includeDeleted ? 1 : 0);

  const [searchText, setSearchText] = useState(q);
  const [syncedQuery, setSyncedQuery] = useState(q);
  if (syncedQuery !== q) {
    setSyncedQuery(q);
    setSearchText(q);
  }
  const [showFilters, setShowFilters] = useState(filtersActive || includeDeleted);
  const [groupBy, setGroupBy] = useState<"date" | "purpose">("date");
  const searchTimer = useRef<number | undefined>(undefined);
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
  const [showQuickExpense, setShowQuickExpense] = useState(false);
  const hasDataRef = useRef(false);
  const fromDateRef = useRef<HTMLInputElement>(null);

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
        api.getHistory(from, to, page, 10, includeDeleted, {
          q: q || undefined,
          envelopeId: envelopeFilter || undefined,
          author,
        }),
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
  }, [client, from, to, page, includeDeleted, q, envelopeFilter, author]);

  useEffect(() => {
    hasDataRef.current = false;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  useEffect(() => () => window.clearTimeout(searchTimer.current), []);

  const groupedByPurpose = useMemo(() => {
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

  const groupedByDate = useMemo(() => {
    const map = new Map<string, HistoryItemDTO[]>();
    const sorted = [...(data?.items ?? [])].sort((a, b) =>
      b.entry.occurredAt.localeCompare(a.entry.occurredAt),
    );
    for (const item of sorted) {
      const key = item.entry.occurredAt;
      const list = map.get(key) ?? [];
      list.push(item);
      map.set(key, list);
    }
    return Array.from(map.entries());
  }, [data]);

  function navigate(next: Record<string, string | number | boolean | undefined>) {
    const params = new URLSearchParams();
    const merged = { from, to, page, includeDeleted, q, envelopeId: envelopeFilter, author, ...next };
    Object.entries(merged).forEach(([key, value]) => {
      if (value !== false && value !== undefined && value !== "") params.set(key, String(value));
    });
    router.push(`/gastos?${params}`);
  }

  function applySearch(text: string) {
    window.clearTimeout(searchTimer.current);
    navigate({ q: text.trim(), page: 0 });
  }

  function changeSearch(text: string) {
    setSearchText(text);
    window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => {
      if (text.trim() !== q) applySearch(text);
    }, SEARCH_DEBOUNCE_MS);
  }

  function clearFilters() {
    window.clearTimeout(searchTimer.current);
    setSearchText("");
    navigate({ q: undefined, envelopeId: undefined, author: undefined, page: 0 });
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
      showToast("Gasto atualizado.");
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
      showToast({
        message: "Gasto excluído.",
        action: {
          label: "Desfazer",
          onClick: () => {
            void createApiClient(client)
              .createEntry(item.entry.envelopeId, {
                kind: item.entry.kind ?? "EXPENSE",
                amount: item.entry.amount,
                occurredAt: item.entry.occurredAt,
                description: item.entry.description,
              })
              .then(() => {
                setFeedback("Exclusão desfeita.");
                void load();
              })
              .catch(() => {
                setFeedback("Não foi possível desfazer a exclusão.");
              });
          },
        },
      });
      void load();
    } catch {
      setFeedback("Não foi possível excluir o gasto.");
    }
  }

  const spendableEnvelopes = envelopes.filter((envelope) => !envelope.archivedAt);
  const canRegister = spendableEnvelopes.length > 0;
  const thisMonthFrom = defaultFrom();
  const previousMonth = previousMonthRange();
  const lastSevenFrom = shiftDays(today, -6);
  const periodPreset =
    from === thisMonthFrom && to === today
      ? "this-month"
      : from === previousMonth.from && to === previousMonth.to
        ? "prev-month"
        : from === lastSevenFrom && to === today
          ? "last-7"
          : "custom";

  function startRegister() {
    setFeedback(null);
    setShowQuickExpense(true);
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

  function renderEntryItem(item: HistoryItemDTO) {
    return (
      <li key={item.entry.id} data-deleted={item.entry.deletedAt ? "true" : "false"}>
        <div>
          <strong>{item.envelopeName}</strong>
          <span>
            {groupBy === "date" ? purposes[item.purpose] : formatDateLabel(item.entry.occurredAt)}
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
                  .then((revPage) => setRevisions(revPage.items))
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
    );
  }

  return (
    <AppShell
      current="expenses"
      email={email}
      onLogout={onLogout}
      onQuickExpense={canRegister ? startRegister : undefined}
    >
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

        <div className={styles.periodChips} role="group" aria-label="Atalhos de período">
          <button
            type="button"
            className={periodPreset === "this-month" ? `${styles.periodChip} ${styles.periodChipActive}` : styles.periodChip}
            aria-pressed={periodPreset === "this-month"}
            onClick={() => navigate({ from: thisMonthFrom, to: today, page: 0 })}
          >
            Este mês
          </button>
          <button
            type="button"
            className={periodPreset === "prev-month" ? `${styles.periodChip} ${styles.periodChipActive}` : styles.periodChip}
            aria-pressed={periodPreset === "prev-month"}
            onClick={() => navigate({ from: previousMonth.from, to: previousMonth.to, page: 0 })}
          >
            Mês passado
          </button>
          <button
            type="button"
            className={periodPreset === "last-7" ? `${styles.periodChip} ${styles.periodChipActive}` : styles.periodChip}
            aria-pressed={periodPreset === "last-7"}
            onClick={() => navigate({ from: lastSevenFrom, to: today, page: 0 })}
          >
            Últimos 7 dias
          </button>
          <button
            type="button"
            className={periodPreset === "custom" ? `${styles.periodChip} ${styles.periodChipActive}` : styles.periodChip}
            aria-pressed={periodPreset === "custom"}
            onClick={() => {
              setShowFilters(true);
              window.requestAnimationFrame(() => fromDateRef.current?.focus());
            }}
          >
            Personalizado
          </button>
          <Button
            type="button"
            variant="secondary"
            aria-expanded={showFilters}
            onClick={() => setShowFilters((open) => !open)}
          >
            {activeExtraFilterCount > 0 ? `Filtros (${activeExtraFilterCount})` : "Filtros"}
          </Button>
        </div>

        <form
          className={styles.filters}
          onSubmit={(event) => {
            event.preventDefault();
            applySearch(searchText);
          }}
        >
          <label className={styles.search}>
            Buscar na descrição
            <input
              type="search"
              value={searchText}
              maxLength={140}
              placeholder="Ex.: farmácia"
              onChange={(event) => changeSearch(event.target.value)}
            />
          </label>
          {showFilters && (
            <>
              <label>
                Verba
                <select
                  value={envelopeFilter}
                  onChange={(event) => navigate({ envelopeId: event.target.value, page: 0 })}
                >
                  <option value="">Todas</option>
                  {envelopes.map((envelope) => (
                    <option key={envelope.id} value={envelope.id}>
                      {envelope.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Autor
                <select value={author ?? ""} onChange={(event) => navigate({ author: event.target.value, page: 0 })}>
                  <option value="">Todos</option>
                  <option value="ME">Lançados por mim</option>
                  <option value="OTHERS">Lançados por outras pessoas</option>
                </select>
              </label>
              <label>
                De
                <input
                  ref={fromDateRef}
                  type="date"
                  value={from}
                  onChange={(event) => navigate({ from: event.target.value, page: 0 })}
                />
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
            </>
          )}
          {filtersActive && (
            <Button variant="secondary" type="button" onClick={clearFilters}>
              Limpar filtros
            </Button>
          )}
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
          <Notice tone="danger">
            {error}{" "}
            <Button variant="text" onClick={() => void load()}>
              Tentar novamente
            </Button>
          </Notice>
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
                <div style={{ display: "flex", alignItems: "center", gap: "0.75rem", flexWrap: "wrap" }}>
                  <div role="group" aria-label="Agrupamento da lista" style={{ display: "flex", gap: "0.35rem" }}>
                    <Button
                      type="button"
                      variant={groupBy === "date" ? "primary" : "secondary"}
                      onClick={() => setGroupBy("date")}
                    >
                      Por data
                    </Button>
                    <Button
                      type="button"
                      variant={groupBy === "purpose" ? "primary" : "secondary"}
                      onClick={() => setGroupBy("purpose")}
                    >
                      Por tipo
                    </Button>
                  </div>
                  <span>Página {(data?.page ?? 0) + 1}</span>
                </div>
              </header>
              {filtersActive && data && (
                <p className={styles.matchedTotal}>
                  Total dos resultados: <strong>{formatBRL(data.matchedTotal.amount)}</strong> (todas as páginas, sem excluídos)
                </p>
              )}
              {(data?.items.length ?? 0) === 0 ? (
                filtersActive ? (
                  <div className={styles.empty}>
                    <p>Nenhum gasto corresponde aos filtros.</p>
                    <Button variant="secondary" onClick={clearFilters}>
                      Limpar filtros
                    </Button>
                  </div>
                ) : (
                  <div className={styles.empty}>
                    <p>Nenhum gasto no período selecionado.</p>
                    {canRegister && <Button onClick={startRegister}>Registrar gasto</Button>}
                  </div>
                )
              ) : groupBy === "date" ? (
                groupedByDate.map(([dateKey, items]) => (
                  <div className={styles.group} key={dateKey}>
                    <h3>{formatDayHeading(dateKey, today)}</h3>
                    <ul>{items.map(renderEntryItem)}</ul>
                  </div>
                ))
              ) : (
                (Object.keys(purposes) as (keyof typeof purposes)[]).map(
                  (purpose) =>
                    groupedByPurpose[purpose].length > 0 && (
                      <div className={styles.group} key={purpose}>
                        <h3>{purposes[purpose]}</h3>
                        <ul>{groupedByPurpose[purpose].map(renderEntryItem)}</ul>
                      </div>
                    ),
                )
              )}
            </section>
            <nav className={styles.pagination} aria-label="Paginação do histórico">
              <Button variant="secondary" disabled={page === 0} onClick={() => navigate({ page: page - 1 })}>
                Anterior
              </Button>
              <Button variant="secondary" disabled={!data?.hasNext} onClick={() => navigate({ page: page + 1 })}>
                Próxima
              </Button>
            </nav>
          </div>
        )}
      </div>

      <QuickExpenseDialog
        open={showQuickExpense}
        envelopes={spendableEnvelopes}
        onClose={() => setShowQuickExpense(false)}
        onSuccess={(result) => {
          setShowQuickExpense(false);
          const msg = `${result.kind === "CONTRIBUTION" ? "Aporte" : "Gasto"} registrado em "${result.envelopeName}"${overdraftNotice(result)}`;
          setFeedback(msg);
          showToast({
            message: msg,
            action: {
              label: "Desfazer",
              onClick: () => {
                void createApiClient(client)
                  .deleteHistoryEntry(result.entryId)
                  .then(() => {
                    setFeedback("Lançamento desfeito.");
                    void load();
                  })
                  .catch(() => {
                    setFeedback("Não foi possível desfazer o lançamento.");
                  });
              },
            },
          });
          void load();
        }}
      />

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
