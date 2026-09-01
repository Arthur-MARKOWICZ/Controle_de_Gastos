"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { Dialog } from "../ui/Dialog";
import { createApiClient, type EnvelopeDTO, type HistoryItemDTO, type HistoryPageDTO, type HistorySummaryDTO } from "../../lib/api";
import { currentMonthSaoPaulo, todaySaoPaulo } from "../../lib/dates";
import { formatBRL, formatBRLInputMask, maskToPlain } from "../../lib/money";
import styles from "./HistoryPage.module.css";
import { AppShell } from "../AppShell/AppShell";

type Props = { email: string; onLogout(): void };
const purposes = { LIMIT: "Limite", GOAL: "Meta de aporte", FIXED: "Compromisso fixo", SAVINGS_TARGET: "Meta de acumulação", ANNUAL_EXPENSE: "Gasto anual" } as const;

function defaultFrom() { return `${currentMonthSaoPaulo()}-01`; }

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
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<HistoryItemDTO | null>(null);
  const [amount, setAmount] = useState("");
  const [envelopeId, setEnvelopeId] = useState("");
  const [description, setDescription] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (from > to) { setError("A data inicial deve ser anterior à final."); setLoading(false); return; }
    setLoading(true); setError(null);
    try {
      const api = createApiClient(client);
      const [history, report, visibleEnvelopes] = await Promise.all([
        api.getHistory(from, to, page, 10, includeDeleted), api.getHistorySummary(from, to), api.getEnvelopes(to.slice(0, 7)),
      ]);
      setData(history); setSummary(report); setEnvelopes(visibleEnvelopes);
    } catch { setError("Não foi possível carregar o histórico."); }
    finally { setLoading(false); }
  }, [client, from, to, page, includeDeleted]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const grouped = useMemo(() => {
    const groups: Record<keyof typeof purposes, HistoryItemDTO[]> = { LIMIT: [], GOAL: [], FIXED: [], SAVINGS_TARGET: [], ANNUAL_EXPENSE: [] };
    for (const item of data?.items ?? []) groups[item.purpose].push(item);
    return groups;
  }, [data]);

  function navigate(next: Record<string, string | number | boolean | undefined>) {
    const params = new URLSearchParams();
    const merged = { from, to, page, includeDeleted, ...next };
    Object.entries(merged).forEach(([key, value]) => { if (value !== false && value !== undefined) params.set(key, String(value)); });
    router.push(`/gastos?${params}`);
  }

  function startEdit(item: HistoryItemDTO) {
    setEditing(item); setEnvelopeId(item.entry.envelopeId); setAmount(formatBRLInputMask(item.entry.amount.amount)); setDescription(item.entry.description ?? "");
  }

  async function submitEdit(event: FormEvent) {
    event.preventDefault();
    const plain = maskToPlain(amount);
    if (!editing || !envelopeId || !/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") return;
    try {
      await createApiClient(client).updateHistoryEntry(editing.entry.id, { envelopeId, amount: { amount: plain, currency: "BRL" }, description: description.trim() || null });
      setEditing(null); setFeedback("Gasto atualizado."); void load();
    } catch { setFeedback("Não foi possível atualizar o gasto."); }
  }

  async function remove(item: HistoryItemDTO) {
    if (!window.confirm("Excluir este gasto? Ele deixará de compor os totais.")) return;
    try { await createApiClient(client).deleteHistoryEntry(item.entry.id); setFeedback("Gasto excluído."); void load(); }
    catch { setFeedback("Não foi possível excluir o gasto."); }
  }

  const card = (label: string, value?: string) => <div className={styles.metric}><dt>{label}</dt><dd>{value ? formatBRL(value) : "—"}</dd></div>;
  const chartMax = Math.max(...(summary?.monthlyTotals.map((total) => Number(total.amount.amount)) ?? [0]), 1);
  const ownerEnvelopes = envelopes.filter((envelope) => envelope.role === "OWNER");

  return <AppShell current="expenses" email={email} onLogout={onLogout}>
    <div className={styles.page}>
      <header className={styles.header}><div><p className={styles.eyebrow}>Registro financeiro</p><h1>Gastos</h1><p>Registre, edite e consulte gastos das verbas que você pode visualizar.</p></div></header>
      <form className={styles.filters} onSubmit={(event) => { event.preventDefault(); navigate({ page: 0 }); }}>
        <label>De<input type="date" value={from} onChange={(event) => navigate({ from: event.target.value, page: 0 })} /></label>
        <label>Até<input type="date" value={to} onChange={(event) => navigate({ to: event.target.value, page: 0 })} /></label>
        <label className={styles.checkbox}><input type="checkbox" checked={includeDeleted} onChange={(event) => navigate({ includeDeleted: event.target.checked, page: 0 })} /> Incluir excluídos</label>
      </form>
      {feedback && <p role="status" className={styles.feedback}>{feedback}</p>}
      {loading && <p role="status" aria-busy="true">Carregando histórico…</p>}
      {error && <p role="alert" className={styles.error}>{error} <button type="button" onClick={() => void load()}>Tentar novamente</button></p>}
      {!loading && !error && summary && <>
        <dl className={styles.metrics}>{card("Renda do período", summary.income.amount)}{card("Gastos do período", summary.expenses.amount)}{card("Saldo líquido", summary.netBalance.amount)}{card("Saldo acumulado", summary.accumulatedBalance.amount)}</dl>
        <section className={styles.charts} aria-label="Gráficos de gastos"><div className={styles.chart}><h2>Gastos por mês</h2>{summary.monthlyTotals.length === 0 ? <p>Sem gastos no período.</p> : <ul>{summary.monthlyTotals.map((total) => <li key={total.month}><span>{total.month}</span><i style={{ width: `${Number(total.amount.amount) / chartMax * 100}%` }} /><strong>{formatBRL(total.amount.amount)}</strong></li>)}</ul>}</div><div className={styles.chart}><h2>Gastos por tipo</h2><ul className={styles.legend}>{summary.purposeTotals.map((total) => <li key={total.purpose}><span className={styles.dot} data-purpose={total.purpose} />{purposes[total.purpose]}<strong>{formatBRL(total.amount.amount)}</strong></li>)}</ul></div></section>
        <section className={styles.entries} aria-labelledby="gastos-titulo"><header><h2 id="gastos-titulo">Gastos</h2><span>Página {data?.page ?? 0 + 1}</span></header>{(data?.items.length ?? 0) === 0 ? <p className={styles.empty}>Nenhum gasto no período selecionado.</p> : (Object.keys(purposes) as (keyof typeof purposes)[]).map((purpose) => grouped[purpose].length > 0 && <div className={styles.group} key={purpose}><h3>{purposes[purpose]}</h3><ul>{grouped[purpose].map((item) => <li key={item.entry.id} data-deleted={item.entry.deletedAt ? "true" : "false"}><div><strong>{item.envelopeName}</strong><span>{item.entry.occurredAt}{item.entry.description ? ` · ${item.entry.description}` : ""}</span></div><strong>{formatBRL(item.entry.amount.amount)}</strong>{item.role === "OWNER" && !item.entry.deletedAt && <span className={styles.actions}><button type="button" onClick={() => startEdit(item)}>Editar</button><button type="button" onClick={() => void remove(item)}>Excluir</button></span>}</li>)}</ul></div>)}</section>
        <nav className={styles.pagination} aria-label="Paginação do histórico"><button type="button" disabled={page === 0} onClick={() => navigate({ page: page - 1 })}>Anterior</button><button type="button" disabled={!data?.hasNext} onClick={() => navigate({ page: page + 1 })}>Próxima</button></nav>
      </>}
    </div>
    <Dialog open={!!editing} onClose={() => setEditing(null)} title="Editar gasto"><form className={styles.editForm} onSubmit={submitEdit}><label>Valor (BRL)<input inputMode="decimal" value={amount} onChange={(event) => setAmount(formatBRLInputMask(event.target.value))} required /></label><label>Verba<select value={envelopeId} onChange={(event) => setEnvelopeId(event.target.value)}>{ownerEnvelopes.map((envelope) => <option key={envelope.id} value={envelope.id}>{envelope.name}</option>)}</select></label><label>Descrição<input value={description} maxLength={140} onChange={(event) => setDescription(event.target.value)} /></label><p>A data do gasto não pode ser alterada.</p><div><button type="button" onClick={() => setEditing(null)}>Cancelar</button><button type="submit">Salvar</button></div></form></Dialog>
  </AppShell>;
}
